package com.free.mqtt.server.qos;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.free.mqtt.server.session.data.StoredMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RetainedRepository {

    private static final ConcurrentHashMap<String, StoredMessage> retainedByTopic = new ConcurrentHashMap<>();
    private static volatile RedisTemplate<String, String> redisTemplate;
    private static final String RETAIN_TOPICS_KEY = "mqtt:retain:topics";
    private static final String RETAIN_DATA_KEY = "mqtt:retain:data";

    public static void init(RedisTemplate<String, String> template) {
        redisTemplate = template;
    }

    public static void store(StoredMessage msg) {
        if (msg == null || msg.getTopic() == null) {
            return;
        }
        if (msg.getPayload() == null || msg.getPayload().length == 0) {
            retainedByTopic.remove(msg.getTopic());
            RedisTemplate<String, String> rt = redisTemplate;
            if (rt != null) {
                try {
                    rt.opsForSet().remove(RETAIN_TOPICS_KEY, msg.getTopic());
                    rt.opsForHash().delete(RETAIN_DATA_KEY, msg.getTopic());
                } catch (Exception ignored) {
                }
            }
            return;
        }
        StoredMessage copy = new StoredMessage(msg.getPayload(), msg.getQos() == null ? MqttQoS.AT_MOST_ONCE : msg.getQos(), msg.getTopic());
        copy.setRetained(true);
        retainedByTopic.put(msg.getTopic(), copy);
        RedisTemplate<String, String> rt = redisTemplate;
        if (rt != null) {
            try {
                rt.opsForSet().add(RETAIN_TOPICS_KEY, msg.getTopic());
                JSONObject o = new JSONObject();
                o.put("qos", (msg.getQos() == null ? MqttQoS.AT_MOST_ONCE : msg.getQos()).value());
                o.put("payloadBase64", Base64.getEncoder().encodeToString(msg.getPayload()));
                rt.opsForHash().put(RETAIN_DATA_KEY, msg.getTopic(), JSON.toJSONString(o));
            } catch (Exception ignored) {
            }
        }
    }

    public static StoredMessage get(String topic) {
        if (topic == null) {
            return null;
        }
        StoredMessage msg = retainedByTopic.get(topic);
        if (msg == null) {
            RedisTemplate<String, String> rt = redisTemplate;
            if (rt == null) {
                return null;
            }
            Object raw = null;
            try {
                raw = rt.opsForHash().get(RETAIN_DATA_KEY, topic);
            } catch (Exception ignored) {
            }
            if (!(raw instanceof String)) {
                return null;
            }
            StoredMessage loaded = parseRetained(topic, (String) raw);
            if (loaded == null) {
                return null;
            }
            retainedByTopic.put(topic, loaded);
            msg = loaded;
        }
        StoredMessage copy = new StoredMessage(msg.getPayload(), msg.getQos(), msg.getTopic());
        copy.setRetained(true);
        return copy;
    }

    public static List<StoredMessage> listMatching(String topicFilter) {
        if (topicFilter == null) {
            return null;
        }
        RedisTemplate<String, String> rt = redisTemplate;
        if (rt != null) {
            Collection<String> topics = null;
            try {
                topics = rt.opsForSet().members(RETAIN_TOPICS_KEY);
            } catch (Exception ignored) {
            }
            if (topics != null && !topics.isEmpty()) {
                List<String> matched = new ArrayList<>();
                for (String t : topics) {
                    if (topicMatches(topicFilter, t)) {
                        matched.add(t);
                    }
                }
                if (!matched.isEmpty()) {
                    List<Object> raws = null;
                    try {
                        List<Object> matchedKeys = new ArrayList<>(matched.size());
                        matchedKeys.addAll(matched);
                        raws = rt.opsForHash().multiGet(RETAIN_DATA_KEY, matchedKeys);
                    } catch (Exception ignored) {
                    }
                    List<StoredMessage> rtv = new ArrayList<>(matched.size());
                    if (raws != null) {
                        for (int i = 0; i < raws.size(); i++) {
                            Object raw = raws.get(i);
                            if (!(raw instanceof String)) {
                                continue;
                            }
                            String t = matched.get(i);
                            StoredMessage loaded = parseRetained(t, (String) raw);
                            if (loaded != null) {
                                retainedByTopic.put(t, loaded);
                                StoredMessage copy = new StoredMessage(loaded.getPayload(), loaded.getQos(), loaded.getTopic());
                                copy.setRetained(true);
                                rtv.add(copy);
                            }
                        }
                    }
                    return rtv;
                }
                return new ArrayList<>();
            }
        }
        List<StoredMessage> rtv = new ArrayList<>();
        for (Map.Entry<String, StoredMessage> e : retainedByTopic.entrySet()) {
            if (topicMatches(topicFilter, e.getKey())) {
                StoredMessage msg = e.getValue();
                if (msg != null) {
                    StoredMessage copy = new StoredMessage(msg.getPayload(), msg.getQos(), msg.getTopic());
                    copy.setRetained(true);
                    rtv.add(copy);
                }
            }
        }
        return rtv;
    }

    private static StoredMessage parseRetained(String topic, String rawJson) {
        if (rawJson == null) {
            return null;
        }
        JSONObject o = null;
        try {
            o = JSON.parseObject(rawJson);
        } catch (Exception ignored) {
        }
        if (o == null) {
            return null;
        }
        Integer qos = o.getInteger("qos");
        String payloadBase64 = o.getString("payloadBase64");
        if (payloadBase64 == null) {
            return null;
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(payloadBase64.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
        StoredMessage msg = new StoredMessage(payload, MqttQoS.valueOf(qos == null ? 0 : qos), topic);
        msg.setRetained(true);
        return msg;
    }

    private static boolean topicMatches(String filter, String topic) {
        if (filter == null || topic == null) {
            return false;
        }
        if (filter.equals(topic)) {
            return true;
        }
        String[] fs = filter.split("/");
        String[] ts = topic.split("/");
        int i = 0;
        for (; i < fs.length; i++) {
            String f = fs[i];
            if (f.equals("#")) {
                return true;
            }
            if (i >= ts.length) {
                return false;
            }
            if (f.equals("+")) {
                continue;
            }
            if (!f.equals(ts[i])) {
                return false;
            }
        }
        return i == ts.length;
    }
}
