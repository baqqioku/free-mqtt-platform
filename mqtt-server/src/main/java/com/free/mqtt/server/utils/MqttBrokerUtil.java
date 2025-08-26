package com.free.mqtt.server.utils;


import com.free.mqtt.server.session.data.StoredMessage;
import com.free.mqtt.server.subscriptions.data.Subscription;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.*;

import java.util.ArrayList;
import java.util.List;

public class MqttBrokerUtil {
    public static int messageId(MqttMessage msg) {
        return ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
    }

    public static MqttQoS lowerQosToTheSubscriptionDesired(Subscription sub, MqttQoS qos) {
        if (qos.value() > sub.getQos().value()) {
            qos = sub.getQos();
        }
        return qos;
    }

    public static MqttPublishMessage mqttPublishMessage(int msgId, StoredMessage pubMsg) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, pubMsg.getQos(), false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(pubMsg.getTopic(), msgId);
        return new MqttPublishMessage(fixedHeader, varHeader, Unpooled.copiedBuffer(pubMsg.getPayload()));
    }


    public static MqttPublishMessage mqttPublishMessage(String topic, String payLoad, MqttQoS qos, int messageId) throws Exception {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, messageId);
        return new MqttPublishMessage(fixedHeader, varHeader, Unpooled.copiedBuffer(payLoad.getBytes("utf-8")));
    }


    public static MqttPubAckMessage mqttMqttPubAckMessage(int messageId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttPubAckMessage pubAckMessage = new MqttPubAckMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageId));

        return pubAckMessage;
    }


    public static MqttMessage pubRecMqttMessage(int messageID) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessage pubRecMessage = new MqttMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageID));

        return pubRecMessage;
    }


    public static MqttMessage pubCompMqttMessage(int messageID) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessage pubCompMessage = new MqttMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageID));

        return pubCompMessage;
    }


    public static MqttMessage pubRelMqttMessage(int messageID) {
        MqttFixedHeader pubRelHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessage pubRelMessage = new MqttMessage(pubRelHeader, MqttMessageIdVariableHeader.from(messageID));

        return pubRelMessage;
    }

    public static MqttMessage getPingMqttMessage() {
        MqttFixedHeader pingHeader = new MqttFixedHeader(
                MqttMessageType.PINGRESP,
                false,
                MqttQoS.AT_MOST_ONCE,
                false,
                0);

        MqttMessage pingResp = new MqttMessage(pingHeader);

        return pingResp;
    }


    public static StoredMessage asStoredMessage(MqttPublishMessage msg, String clientId) {
        ByteBuf payload = msg.payload();
        byte[] payloadContent = new byte[payload.readableBytes()];

        int mark = payload.readerIndex();
        payload.readBytes(payloadContent);
        payload.readerIndex(mark);

        StoredMessage stored = new StoredMessage(payloadContent, msg.fixedHeader().qosLevel(), msg.variableHeader().topicName());
        stored.setRetained(msg.fixedHeader().isRetain());
        stored.setClientID(clientId);

        return stored;
    }

    public static MqttConnAckMessage mqttConnAckMessage(MqttConnectReturnCode returnCode, boolean sessionPresent) {
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttConnAckVariableHeader mqttConnAckVariableHeader = new MqttConnAckVariableHeader(returnCode, sessionPresent);

        return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
    }

    public static MqttSubAckMessage mqttSubAckMessage(List<MqttTopicSubscription> topicFilters, int messageId) {
        List<Integer> grantedQoSLevels = new ArrayList<>();
        for (MqttTopicSubscription req : topicFilters) {
            grantedQoSLevels.add(req.qualityOfService().value());
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttSubAckPayload payload = new MqttSubAckPayload(grantedQoSLevels);
        return new MqttSubAckMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageId), payload);
    }


    public static MqttUnsubAckMessage mqttUnsubAckMessage(int messageId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        return new MqttUnsubAckMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageId));
    }
}
