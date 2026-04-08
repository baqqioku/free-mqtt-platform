package com.free.mqtt.server.qos;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.io.Serializable;
import java.nio.ByteBuffer;

public class StoredMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String msgUUID;
    private ByteBuffer payload;
    private MqttQoS qos;
    private boolean retained;
    private String clientID;
    
    public StoredMessage() {
    }
    
    public StoredMessage(ByteBuffer payload, MqttQoS qos, boolean retained) {
        this.payload = payload;
        this.qos = qos;
        this.retained = retained;
    }
    
    public String getMsgUUID() {
        return msgUUID;
    }
    
    public void setMsgUUID(String msgUUID) {
        this.msgUUID = msgUUID;
    }
    
    public ByteBuffer getPayload() {
        return payload;
    }
    
    public void setPayload(ByteBuffer payload) {
        this.payload = payload;
    }
    
    public MqttQoS getQos() {
        return qos;
    }
    
    public void setQos(MqttQoS qos) {
        this.qos = qos;
    }
    
    public boolean isRetained() {
        return retained;
    }
    
    public void setRetained(boolean retained) {
        this.retained = retained;
    }
    
    public String getClientID() {
        return clientID;
    }
    
    public void setClientID(String clientID) {
        this.clientID = clientID;
    }
}
