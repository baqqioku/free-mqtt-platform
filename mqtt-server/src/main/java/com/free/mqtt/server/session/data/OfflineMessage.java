package com.free.mqtt.server.session.data;

import java.io.Serializable;

public class OfflineMessage implements Serializable {

    /**
     * List or Sorted Set: mqtt:session:{clientId}:offline
     *
     * 值结构可以是 JSON 串，也可以序列化二进制
     *
     * 可加过期时间控制，避免爆内存
     */

    private String topic;

    private String payload;

    private int qos;

    private long timestamp;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
