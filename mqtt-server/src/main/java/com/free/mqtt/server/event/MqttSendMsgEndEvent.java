package com.free.mqtt.server.event;

import com.free.mqtt.server.session.data.StoredMessage;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.nio.charset.StandardCharsets;

public class MqttSendMsgEndEvent extends MqttBaseEvent {

    private final String topic;

    private final Integer pushMessageId;

    private final MqttQoS qos;

    private final String msgUUID;

    private final String payloadText;

    private final boolean isAck; // true for ACK (delete from queue), false for SENT (mark in-flight)

    public MqttSendMsgEndEvent(String clientId, StoredMessage storedMessage, boolean isAck) {
        super(clientId);
        this.topic = storedMessage == null ? null : storedMessage.getTopic();
        this.pushMessageId = storedMessage == null ? null : storedMessage.getBusinessMsgId();
        this.qos = storedMessage == null ? null : storedMessage.getQos();
        this.msgUUID = storedMessage == null ? null : storedMessage.getMsgUUID();
        byte[] payload = storedMessage == null ? null : storedMessage.getPayload();
        this.payloadText = payload == null ? null : new String(payload, StandardCharsets.UTF_8);
        this.isAck = isAck;
    }

    public MqttSendMsgEndEvent(String clientId, String topic, MqttQoS qos, Integer pushMessageId, String msgUUID, boolean isAck) {
        super(clientId);
        this.topic = topic;
        this.qos = qos;
        this.pushMessageId = pushMessageId;
        this.msgUUID = msgUUID;
        this.payloadText = null;
        this.isAck = isAck;
    }

    public String getTopic() {
        return topic;
    }

    public Integer getPushMessageId() {
        return pushMessageId;
    }

    public MqttQoS getQos() {
        return qos;
    }

    public String getMsgUUID() {
        return msgUUID;
    }

    public String getPayloadText() {
        return payloadText;
    }

    public boolean isAck() {
        return isAck;
    }
}
