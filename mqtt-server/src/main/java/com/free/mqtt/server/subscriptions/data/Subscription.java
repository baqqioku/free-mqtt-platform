package com.free.mqtt.server.subscriptions.data;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.io.Serializable;

public class Subscription implements Serializable {

    private MqttQoS qos;

    private String clientId;

    private Topic topicFilter;

    private boolean alive;

    public Subscription(String clientId, Topic topicFilter) {
        this.clientId = clientId;
        this.topicFilter = topicFilter;
    }

    public Subscription(String clientId, MqttQoS qos, Topic topicFilter) {
        this.qos = qos;
        this.clientId = clientId;
        this.topicFilter = topicFilter;
    }

    public MqttQoS getQos() {
        return qos;
    }

    public void setQos(MqttQoS qos) {
        this.qos = qos;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Topic getTopicFilter() {
        return topicFilter;
    }

    public void setTopicFilter(Topic topicFilter) {
        this.topicFilter = topicFilter;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    /**
     * 这里必须实现equals 和hashCode,否则装进set 会重复
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;

        if(null == clientId){
            return false;
        }

        Subscription that = (Subscription) o;

        if (!clientId.equals(that.clientId)){
            return false;
        }

        if( !topicFilter.equals(that.getTopicFilter()) ){
            return false;
        }

        return true;
    }



    @Override
    public String toString() {
        return "Subscription [clientId=" + clientId + ", topicFilter=" + topicFilter + "]";
    }

    @Override
    public Subscription clone() {
        try {
            return (Subscription) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @Override
    public int hashCode(){
        return clientId.hashCode();
/*    	int result = clientId != null ? clientId.hashCode() : 0;
    	return 31 * result + (topicFilter != null ? topicFilter.hashCode() : 0);*/
    }
}
