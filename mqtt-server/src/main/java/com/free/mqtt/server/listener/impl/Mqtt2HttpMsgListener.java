package com.free.mqtt.server.listener.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.free.common.constant.MqttConstant;
import com.free.common.mqtt.OfflineMsgEntry;
import com.free.common.utils.TopicUtil;
import com.free.mqtt.MqttServer;
import com.free.mqtt.server.event.MqttSendMsgEndEvent;
import com.free.mqtt.server.listener.MqttMsgListener;
import com.free.mqtt.server.session.data.StoredMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.RedisCallback;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.nio.charset.StandardCharsets;

import static com.free.common.constant.RedisKeyConstant.USER_STATUS;
import static com.free.common.constant.RedisKeyConstant.MQTT_OFFLINE;


/**
 * http 消息转mqtt 消息回调接口，异步处理
 */
public class Mqtt2HttpMsgListener extends Thread implements MqttMsgListener {

    private static final Logger logger = LoggerFactory.getLogger(Mqtt2HttpMsgListener.class);

    private static final String ORDER_SUFFIX = ":order";
    private static final String INFLIGHT_SUFFIX = ":inflight";
    private static final String EXPIRE_SUFFIX = ":expire";
    private static final String DATA_SUFFIX = ":data";

    private static final RedisScript<List> CLAIM_SCRIPT;
    private static final byte[] UPSERT_AND_TRIM_SCRIPT_BYTES;

    private RedisTemplate<String,String> redisTemplate;

    private MqttServer mqttServer;

    private Map<String, Long> disConnectTimeMap = new ConcurrentHashMap<String, Long>();
    private Map<String, Long> clientIdToUserIdMap = new ConcurrentHashMap<String, Long>();

    private final LinkedBlockingQueue<OfflineMsgEntry> storeQueue = new LinkedBlockingQueue<>(20000);
    private final ExecutorService storeExecutor;

    static {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        script.setScriptText(
                "local order=KEYS[1]\n" +
                "local inflight=KEYS[2]\n" +
                "local expire=KEYS[3]\n" +
                "local data=KEYS[4]\n" +
                "local now=tonumber(ARGV[1])\n" +
                "local max=tonumber(ARGV[2])\n" +
                "local delay=tonumber(ARGV[3])\n" +
                "if max <= 0 then return {} end\n" +
                "local expired=redis.call('ZRANGEBYSCORE', expire, '-inf', now)\n" +
                "if expired and #expired>0 then\n" +
                "  redis.call('ZREM', order, unpack(expired))\n" +
                "  redis.call('ZREM', inflight, unpack(expired))\n" +
                "  redis.call('ZREM', expire, unpack(expired))\n" +
                "  redis.call('HDEL', data, unpack(expired))\n" +
                "end\n" +
                "local res={}\n" +
                "local inflightIds=redis.call('ZRANGEBYSCORE', inflight, '-inf', now, 'LIMIT', 0, max)\n" +
                "if inflightIds and #inflightIds>0 then\n" +
                "  for i=1,#inflightIds do\n" +
                "    if delay and delay>0 then redis.call('ZADD', inflight, now+delay, inflightIds[i]) end\n" +
                "    local v=redis.call('HGET', data, inflightIds[i])\n" +
                "    if v then table.insert(res, v) end\n" +
                "  end\n" +
                "end\n" +
                "local remaining=max-#res\n" +
                "if remaining>0 then\n" +
                "  local ids=redis.call('ZRANGE', order, 0, remaining-1)\n" +
                "  if ids and #ids>0 then\n" +
                "    for i=1,#ids do\n" +
                "      redis.call('ZREM', order, ids[i])\n" +
                "      if delay and delay>0 then redis.call('ZADD', inflight, now+delay, ids[i]) else redis.call('ZADD', inflight, now, ids[i]) end\n" +
                "      local v=redis.call('HGET', data, ids[i])\n" +
                "      if v then table.insert(res, v) end\n" +
                "    end\n" +
                "  end\n" +
                "end\n" +
                "return res\n"
        );
        CLAIM_SCRIPT = script;
    }

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

    public void run() {

        while(true){
            Iterator<String> it = disConnectTimeMap.keySet().iterator();
            while( it.hasNext() ){
                String clientId = it.next();

                if( checkIdle(clientId) ){
                    cleanClientInfo(clientId);
                }
            }

            try {
                Thread.sleep(2*60*1000L);
            } catch (InterruptedException e) {
            }
        }
    }

    private boolean checkIdle(String clientId){
        Long upTime = disConnectTimeMap.get(clientId);
        if(null == upTime){
            return false;
        }

        if( System.currentTimeMillis() - upTime.longValue() > 5*60*1000L ){
            return true;
        }

        return false;
    }

    public Mqtt2HttpMsgListener(RedisTemplate<String, String> redisTemplate, MqttServer mqttServer) {
        this.redisTemplate = redisTemplate;
        this.mqttServer = mqttServer;
        this.storeExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "offline-store");
                t.setDaemon(true);
                return t;
            }
        });
        this.storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                storeLoop();
            }
        });
        start();
    }

    @Override
    public boolean mqttChannelAuth(String clientId, String userName, String password) {
        boolean authSuccess = false;
        try {
            // 格式验证：clientId 应为 {prefix}_{userId}，如 client_12345
            // 1. 检查 clientId 不为空且长度合理
            if (clientId == null || clientId.trim().isEmpty()) {
                logger.warn("mqtt授权失败，clientId为空");
                return false;
            }
            if (clientId.length() > 256) {
                logger.warn("mqtt授权失败，clientId长度超过256: clientId={}", clientId);
                return false;
            }
            
            // 2. 分离 prefix 和 userId
            String[] parts = clientId.split("_");
            if (parts.length < 2) {
                logger.warn("mqtt授权失败，clientId格式不正确(缺少_分隔符)，应为{prefix}_{userId}: clientId={}", clientId);
                return false;
            }
            
            String userId = parts[parts.length - 1];
            
            // 3. 验证 userId 是否为有效的数字
            if (userId == null || userId.trim().isEmpty()) {
                logger.warn("mqtt授权失败，userId为空: clientId={}", clientId);
                return false;
            }
            if (!userId.matches("^\\d+$")) {
                logger.warn("mqtt授权失败，userId不是有效的数字: userId={}, clientId={}", userId, clientId);
                return false;
            }
            
            // 4. 检查 userName 和 password 不为空
            if (userName == null || userName.trim().isEmpty() || 
                password == null || password.trim().isEmpty()) {
                logger.warn("mqtt授权失败，userName或password为空: clientId={}", clientId);
                return false;
            }
            
            // 5. 从 Redis 查询用户状态
            String userJson = redisTemplate.opsForValue().get(USER_STATUS + userId);
            if (userJson == null) {
                logger.warn("mqtt授权失败，用户状态不存在: userId={}, clientId={}", userId, clientId);
                return false;
            }
            
            // 6. 验证用户名和密码
            JSONObject jsonObject = JSON.parseObject(userJson);
            String storedUserName = jsonObject.getString("userName");
            String storedToken = jsonObject.getString("token");
            
            if (storedUserName == null || storedToken == null) {
                logger.warn("mqtt授权失败，Redis中用户数据不完整: userId={}, clientId={}", userId, clientId);
                return false;
            }
            
            if (userName.equals(storedUserName) && password.equals(storedToken)) {
                authSuccess = true;
                addClientInfo(clientId, Long.valueOf(userId));
                logger.info("mqtt授权成功: clientId={}, userId={}", clientId, userId);
            } else {
                logger.warn("mqtt授权失败，用户名或密码不匹配: userId={}, clientId={}", userId, clientId);
            }
        } catch (NumberFormatException e) {
            logger.error("mqtt授权失败，userId转换为long出错: clientId={}, userName:{}", clientId, userName, e);
            authSuccess = false;
        } catch (Exception e) {
            logger.error("mqtt授权失败，发生异常: clientId={}, userName={}", clientId, userName, e);
            authSuccess = false;
        }
        return authSuccess;
    }

    @Override
    public void notifySendMsgOk(MqttSendMsgEndEvent pushMsgEndInfo) {
        if (pushMsgEndInfo == null || pushMsgEndInfo.getTopic() == null || pushMsgEndInfo.getMsgUUID() == null) {
            return;
        }
        Long userId = TopicUtil.getIdFromTopic(pushMsgEndInfo.getTopic());
        if (userId != null && userId > 0) {
            if (pushMsgEndInfo.isAck()) {
                ackMessage(userId, pushMsgEndInfo.getMsgUUID());
            } else {
                long delay = 2;
                try {
                    delay = mqttServer.getMqttConfig().getRetrySendDelay();
                } catch (Exception ignored) {
                }
                markInflight(userId, pushMsgEndInfo.getMsgUUID(), (System.currentTimeMillis() / 1000) + delay);
            }
        }
    }

    @Override
    public void notifyDisconnect(String clientId) {
        disConnectTimeMap.put(clientId, System.currentTimeMillis());

        // 异步更新 user:status:{userId} 的 online=false
        final Long userId = clientIdToUserIdMap.get(clientId);
        if (userId != null && userId > 0) {
            storeExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        updateUserOnlineStatus(userId, false);
                        logger.info("用户断连，状态更新为离线: userId={}, clientId={}", userId, clientId);
                    } catch (Exception e) {
                        logger.warn("更新用户离线状态失败: userId={}, err={}", userId, e.getMessage());
                    }
                }
            });
        }
    }

    @Override
    public String autoSub(String clientId) {
        Long userId = clientIdToUserIdMap.get(clientId);
        if(userId == null){
            return null;
        }
        String autoSubTopic = MqttConstant.brokerToClientTopic+userId;
        return autoSubTopic;
    }

    @Override
    public void storeOfflineMessage(long userId, StoredMessage msg) {
        if (userId <= 0 || msg == null) {
            return;
        }
        OfflineMsgEntry entry = new OfflineMsgEntry();
        entry.userId = userId;
        entry.topic = msg.getTopic();
        entry.payloadBase64 = Base64.getEncoder().encodeToString(msg.getPayload());
        entry.qos = msg.getQos() != null ? msg.getQos().value() : MqttQoS.AT_LEAST_ONCE.value();
        entry.businessMsgId = msg.getBusinessMsgId();
        entry.createTime = msg.getCreateTime();
        entry.ttl = msg.getTtl();
        entry.msgUUID = (msg.getMsgUUID() == null || msg.getMsgUUID().trim().isEmpty())
                ? UUID.randomUUID().toString().replaceAll("-", "")
                : msg.getMsgUUID();
        entry.expireAt = computeExpireAtSeconds(entry.createTime, entry.ttl);

        long nowSec = System.currentTimeMillis() / 1000;
        if (entry.expireAt <= nowSec) {
            return;
        }

        try {
            writeBatch(Collections.singletonList(entry));
        } catch (Exception e) {
            logger.warn("store offline message sync failed, fallback to queue. userId={}, topic={}, msgUUID={}",
                    userId, entry.topic, entry.msgUUID, e);
            storeQueue.offer(entry);
        }
    }

    @Override
    public List<StoredMessage> popOfflineMessages(String clientId, int maxCount) {
        long userId = parseUserIdFromClientId(clientId);
        if (userId <= 0) {
            return null;
        }
        int limit = maxCount <= 0 ? 200 : maxCount;
        List<String> keys = offlineKeys(userId);
        long nowSec = System.currentTimeMillis() / 1000;

        List raws = null;
        try {
            int delay = 2;
            try {
                delay = mqttServer.getMqttConfig().getRetrySendDelay();
            } catch (Exception ignored) {
            }
            raws = redisTemplate.execute(CLAIM_SCRIPT, keys, String.valueOf(nowSec), String.valueOf(limit), String.valueOf(delay));
        } catch (Exception ignored) {
        }

        List<StoredMessage> rtv = new ArrayList<>(Math.min(limit, 64));
        if (raws != null) {
            for (Object o : raws) {
                if (!(o instanceof String)) {
                    continue;
                }
                StoredMessage msg = toStoredMessage((String) o, nowSec);
                if (msg != null) {
                    rtv.add(msg);
                }
            }
        }

        if (!rtv.isEmpty()) {
            return rtv;
        }

        String legacyKey = MQTT_OFFLINE + userId;
        for (int i = 0; i < limit; i++) {
            String raw = null;
            try {
                raw = redisTemplate.opsForList().leftPop(legacyKey);
            } catch (Exception e) {
                break;
            }
            if (raw == null) {
                break;
            }
            StoredMessage msg = toStoredMessage(raw, nowSec);
            if (msg != null) {
                rtv.add(msg);
            }
        }
        return rtv.isEmpty() ? null : rtv;
    }

    private void markInflight(long userId, String msgUUID, long sendAtSec) {
        if (userId <= 0 || msgUUID == null || msgUUID.trim().isEmpty()) {
            return;
        }
        String base = MQTT_OFFLINE + userId;
        byte[] orderKey = bytes(base + ORDER_SUFFIX);
        byte[] inflightKey = bytes(base + INFLIGHT_SUFFIX);
        byte[] member = bytes(msgUUID);
        long expireSeconds = 86400;
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zRem(orderKey, member);
            connection.zAdd(inflightKey, (double) sendAtSec, member);
            connection.expire(inflightKey, expireSeconds);
            return null;
        });
    }

    private void ackMessage(long userId, String msgUUID) {
        if (userId <= 0 || msgUUID == null || msgUUID.trim().isEmpty()) {
            return;
        }
        String base = MQTT_OFFLINE + userId;
        byte[] orderKey = bytes(base + ORDER_SUFFIX);
        byte[] inflightKey = bytes(base + INFLIGHT_SUFFIX);
        byte[] expireKey = bytes(base + EXPIRE_SUFFIX);
        byte[] dataKey = bytes(base + DATA_SUFFIX);
        byte[] member = bytes(msgUUID);
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zRem(orderKey, member);
            connection.zRem(inflightKey, member);
            connection.zRem(expireKey, member);
            connection.hDel(dataKey, member);
            return null;
        });
    }

    /*单线程调用*/
    public void addClientInfo(String clientId, Long userId) {
        synchronized (this) {
            disConnectTimeMap.remove(clientId);
            clientIdToUserIdMap.put(clientId, userId);
            // 客户端连入，更新 user:status:{userId} 的 online=true
            try {
                updateUserOnlineStatus(userId, true);
            } catch (Exception e) {
                logger.warn("更新用户在线状态失败: userId={}, err={}", userId, e.getMessage());
            }
        }
    }

    /*多线程调用*/
    private void cleanClientInfo(String clientId) {
        synchronized (this) {
            if( !checkIdle(clientId) ){
                return;
            }

            logger.info("空闲移除  clientId:" + clientId);

            Long userId = clientIdToUserIdMap.get(clientId);
            disConnectTimeMap.remove(clientId);
            clientIdToUserIdMap.remove(clientId);

            // 5分钟空闲后也确保用户状态为离线
            if (userId != null && userId > 0) {
                try {
                    updateUserOnlineStatus(userId, false);
                } catch (Exception e) {
                    logger.warn("更新用户离线状态失败: userId={}, err={}", userId, e.getMessage());
                }
            }
        }
    }

    private long parseUserIdFromClientId(String clientId) {
        if (clientId == null) {
            return -1;
        }
        try {
            String[] arr = clientId.split("_");
            if (arr.length < 2) {
                return -1;
            }
            return Long.parseLong(arr[arr.length - 1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private void storeLoop() {
        while (true) {
            List<OfflineMsgEntry> batch = new ArrayList<>(256);
            try {
                OfflineMsgEntry first = storeQueue.poll(2, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                storeQueue.drainTo(batch, 255);
            } catch (Exception e) {
                continue;
            }
            try {
                writeBatch(batch);
            } catch (Exception e) {
                logger.warn("flush offline batch failed, requeue size={}", batch.size(), e);
                for (OfflineMsgEntry entry : batch) {
                    if (entry != null) {
                        storeQueue.offer(entry);
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
        final int maxSize;
        try {
            maxSize = mqttServer.getMqttConfig().getReliableQueueMaxSize();
        } catch (Exception e) {
            return;
        }
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
            long ttlSeconds = Math.min(Math.max((entry.expireAt - nowSec) + 3600, 3600), 86400);
            byte[] json = bytes(JSON.toJSONString(entry));
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

    private StoredMessage toStoredMessage(String rawJson, long nowSec) {
        OfflineMsgEntry entry = null;
        try {
            entry = JSON.parseObject(rawJson, OfflineMsgEntry.class);
        } catch (Exception ignored) {
        }
        if (entry == null || entry.topic == null || entry.payloadBase64 == null) {
            return null;
        }
        long expireAt = entry.expireAt > 0 ? entry.expireAt : computeExpireAtSeconds(entry.createTime, entry.ttl);
        if (expireAt <= nowSec) {
            return null;
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(entry.payloadBase64);
        } catch (Exception e) {
            return null;
        }
        MqttQoS qos = MqttQoS.valueOf(entry.qos);
        StoredMessage msg = new StoredMessage(payload, qos, entry.topic, "system", entry.businessMsgId, entry.ttl, entry.msgUUID);
        msg.setCreateTime(entry.createTime);
        return msg;
    }

    private long computeExpireAtSeconds(long createTimeMillis, long ttlSeconds) {
        long createSec = createTimeMillis > 0 ? (createTimeMillis / 1000) : (System.currentTimeMillis() / 1000);
        if (ttlSeconds > 0) {
            return createSec + ttlSeconds;
        }
        return createSec + 86400;
    }

    private List<String> offlineKeys(long userId) {
        String base = MQTT_OFFLINE + userId;
        return new ArrayList<>(java.util.Arrays.asList(base + ORDER_SUFFIX, base + INFLIGHT_SUFFIX, base + EXPIRE_SUFFIX, base + DATA_SUFFIX));
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 更新 user:status:{userId} 中的 online 字段
     * 读取Redis中的JSON，修改online值，再写回
     */
    private void updateUserOnlineStatus(Long userId, boolean online) {
        String key = USER_STATUS + userId;
        String userJson = redisTemplate.opsForValue().get(key);
        if (userJson == null) {
            return;
        }
        JSONObject jsonObject = JSON.parseObject(userJson);
        jsonObject.put("online", online);
        redisTemplate.opsForValue().set(key, jsonObject.toJSONString());
    }

}
