package com.free.route.service;

import com.alibaba.fastjson.JSON;
import com.free.common.constant.MqttConstant;
import com.free.common.mqtt.OfflineMsgEntry;
import com.free.route.ao.PushMsgAo;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.free.common.constant.RedisKeyConstant.MQTT_OFFLINE;

@Service
public class OfflineStoreService implements DisposableBean {

    private static final String ORDER_SUFFIX = ":order";
    private static final String INFLIGHT_SUFFIX = ":inflight";
    private static final String EXPIRE_SUFFIX = ":expire";
    private static final String DATA_SUFFIX = ":data";
    private static final byte[] UPSERT_AND_TRIM_SCRIPT_BYTES;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private int reliableQueueMaxSize;

    private final LinkedBlockingQueue<OfflineMsgEntry> queue = new LinkedBlockingQueue<>(50000);
    private final ExecutorService executor;

    static {
        String s =
                "local order=KEYS[1]\n" +
                "local inflight=KEYS[2]\n" +
                "local expire=KEYS[3]\n" +
                "local data=KEYS[4]\n" +
                "local id=ARGV[1]\n" +
                "local create=tonumber(ARGV[2])\n" +
                "local exp=tonumber(ARGV[3])\n" +
                "local json=ARGV[4]\n" +
                "local now=tonumber(ARGV[5])\n" +
                "local keyTtl=tonumber(ARGV[6])\n" +
                "local max=tonumber(ARGV[7])\n" +
                "if exp <= now then return 0 end\n" +
                "redis.call('HSET', data, id, json)\n" +
                "redis.call('ZADD', expire, exp, id)\n" +
                "if (redis.call('ZSCORE', inflight, id) == false) and (redis.call('ZSCORE', order, id) == false) then\n" +
                "  redis.call('ZADD', order, create, id)\n" +
                "end\n" +
                "if keyTtl and keyTtl > 0 then\n" +
                "  redis.call('EXPIRE', order, keyTtl)\n" +
                "  redis.call('EXPIRE', inflight, keyTtl)\n" +
                "  redis.call('EXPIRE', expire, keyTtl)\n" +
                "  redis.call('EXPIRE', data, keyTtl)\n" +
                "end\n" +
                "if max and max > 0 then\n" +
                "  local cnt=redis.call('ZCARD', order) + redis.call('ZCARD', inflight)\n" +
                "  local overflow=cnt-max\n" +
                "  if overflow > 0 then\n" +
                "    local victims=redis.call('ZRANGE', order, 0, overflow-1)\n" +
                "    if victims and #victims > 0 then\n" +
                "      redis.call('ZREM', order, unpack(victims))\n" +
                "      redis.call('ZREM', expire, unpack(victims))\n" +
                "      redis.call('HDEL', data, unpack(victims))\n" +
                "    end\n" +
                "  end\n" +
                "end\n" +
                "return 1\n";
        UPSERT_AND_TRIM_SCRIPT_BYTES = s.getBytes(StandardCharsets.UTF_8);
    }

    public OfflineStoreService() {
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "route-offline-store");
                t.setDaemon(true);
                return t;
            }
        });
        this.executor.submit(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        });
    }

    @Value("${mqtt.metric.config.reliableQueueMaxSize:5000}")
    public void setReliableQueueMaxSize(int reliableQueueMaxSize) {
        this.reliableQueueMaxSize = reliableQueueMaxSize;
    }

    public <T> void enqueue(PushMsgAo<T> pushMsgAo) {
        if (pushMsgAo == null || pushMsgAo.getUserId() <= 0) {
            return;
        }
        OfflineMsgEntry entry = new OfflineMsgEntry();
        entry.userId = pushMsgAo.getUserId();
        entry.topic = MqttConstant.brokerToClientTopic + pushMsgAo.getUserId();
        entry.qos = 1;
        entry.businessMsgId = pushMsgAo.getMessageId();
        entry.createTime = pushMsgAo.getCreateTime();
        if (entry.createTime <= 0) {
            entry.createTime = System.currentTimeMillis();
            pushMsgAo.setCreateTime(entry.createTime);
        }
        entry.ttl = pushMsgAo.getTtl();
        entry.expireAt = computeExpireAtSeconds(entry.createTime, entry.ttl);

        String msgUUID = pushMsgAo.getMsgUUID();
        if (msgUUID == null || msgUUID.trim().isEmpty()) {
            msgUUID = UUID.randomUUID().toString().replaceAll("-", "");
            pushMsgAo.setMsgUUID(msgUUID);
        }
        entry.msgUUID = msgUUID;

        long nowSec = System.currentTimeMillis() / 1000;
        if (entry.expireAt <= nowSec) {
            return;
        }

        byte[] payload = JSON.toJSONString(pushMsgAo).getBytes(StandardCharsets.UTF_8);
        entry.payloadBase64 = Base64.getEncoder().encodeToString(payload);

        List<OfflineMsgEntry> one = new ArrayList<>(1);
        one.add(entry);
        try {
            writeBatch(one);
        } catch (Exception e) {
            queue.offer(entry);
        }
    }

    private void loop() {
        while (true) {
            List<OfflineMsgEntry> batch = new ArrayList<>(256);
            try {
                OfflineMsgEntry first = queue.poll(2, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, 255);
            } catch (Exception e) {
                continue;
            }
            try {
                writeBatch(batch);
            } catch (Exception e) {
                for (OfflineMsgEntry entry : batch) {
                    if (entry != null) {
                        queue.offer(entry);
                    }
                }
            }
        }
    }

    private void writeBatch(final List<OfflineMsgEntry> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        final long nowSec = System.currentTimeMillis() / 1000;
        final int maxSize = reliableQueueMaxSize;
        for (OfflineMsgEntry entry : batch) {
            if (entry == null || entry.userId <= 0) {
                continue;
            }
            if (entry.msgUUID == null || entry.msgUUID.trim().isEmpty()) {
                entry.msgUUID = UUID.randomUUID().toString().replaceAll("-", "");
            }
            if (entry.expireAt <= nowSec) {
                continue;
            }
            String base = MQTT_OFFLINE + entry.userId;
            byte[] orderKey = bytes(base + ORDER_SUFFIX);
            byte[] inflightKey = bytes(base + INFLIGHT_SUFFIX);
            byte[] expireKey = bytes(base + EXPIRE_SUFFIX);
            byte[] dataKey = bytes(base + DATA_SUFFIX);
            byte[] json = bytes(JSON.toJSONString(entry));

            long ttlSeconds = Math.min(Math.max((entry.expireAt - nowSec) + 3600, 3600), 86400);
            redisTemplate.execute((RedisCallback<Object>) connection -> connection.eval(
                    UPSERT_AND_TRIM_SCRIPT_BYTES,
                    ReturnType.INTEGER,
                    4,
                    orderKey, inflightKey, expireKey, dataKey,
                    bytes(entry.msgUUID),
                    bytes(String.valueOf(entry.createTime)),
                    bytes(String.valueOf(entry.expireAt)),
                    json,
                    bytes(String.valueOf(nowSec)),
                    bytes(String.valueOf(ttlSeconds)),
                    bytes(String.valueOf(maxSize))
            ));
        }
    }

    private long computeExpireAtSeconds(long createTimeMillis, long ttlSeconds) {
        long createSec = createTimeMillis > 0 ? (createTimeMillis / 1000) : (System.currentTimeMillis() / 1000);
        if (ttlSeconds > 0) {
            return createSec + ttlSeconds;
        }
        return createSec + 86400;
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void destroy() {
        try {
            executor.shutdownNow();
        } catch (Exception ignored) {
        }
    }
}
