package com.free.mqtt.server.event;

public class MqttDisconnectEvent extends MqttBaseEvent {

    private final String remoteIp;

    public MqttDisconnectEvent(String clientId, String remoteIp) {
        super(clientId);
        this.remoteIp = remoteIp;
    }

    public String getRemoteIp() {
        return remoteIp;
    }
}
