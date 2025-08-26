package com.free.mqtt.server.session.data;

import java.io.Serializable;

public class WillMessage implements Serializable {

    public String topic;
    public String message;
    public int qos;
    private long expiryTime; //毫秒时间戳，到期不再发送

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }
}
