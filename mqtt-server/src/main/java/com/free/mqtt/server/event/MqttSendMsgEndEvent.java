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

    public MqttSendMsgEndEvent(String clientId, StoredMessage storedMessage) {
        super(clientId);
        this.topic = storedMessage == null ? null : storedMessage.getTopic();
        this.pushMessageId = storedMessage == null ? null : storedMessage.getBusinessMsgId();
        this.qos = storedMessage == null ? null : storedMessage.getQos();
        this.msgUUID = storedMessage == null ? null : storedMessage.getMsgUUID();
        byte[] payload = storedMessage == null ? null : storedMessage.getPayload();
        this.payloadText = payload == null ? null : new String(payload, StandardCharsets.UTF_8);
    }

    public MqttSendMsgEndEvent(String clientId, String topic, MqttQoS qos, Integer pushMessageId) {
        super(clientId);
        this.topic = topic;
        this.qos = qos;
        this.pushMessageId = pushMessageId;
        this.msgUUID = null;
        this.payloadText = null;
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
}
