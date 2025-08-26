package com.free.mqtt.server.session.data;

import java.io.Serializable;

public class MqttSubscription implements Serializable {

    /**
     * Redis 存储建议：
     *
     * Set: mqtt:session:{clientId}:subs
     *
     * 每个成员结构：{qos}:{topicFilter} 例如：1:/sensor/+/temp
     */

    private String clientId;

    private String topicFilter; // e.q. /sensor/+/temp

    private int qos;

    public MqttSubscription(String topicFilter, int qos) {
        this.topicFilter = topicFilter;
        this.qos = qos;
    }

    public String getTopicFilter() {
        return topicFilter;
    }

    public void setTopicFilter(String topicFilter) {
        this.topicFilter = topicFilter;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
