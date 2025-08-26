package com.free.mqtt.server.event;

public class MqttBaseEvent {

    protected String clientId;

    protected long createTime;

    public MqttBaseEvent(String clientId){
        this.clientId = clientId;

        createTime = System.currentTimeMillis();
    }

    public String getClientId() {
        return clientId;
    }

    public long getCreateTime() {
        return createTime;
    }
}
