package com.free.mqtt.server.qos;

import com.free.mqtt.server.auth.IAuthorizator;
import com.free.mqtt.server.interceptor.Interceptor;
import com.free.mqtt.server.session.SessionRepository;
import com.free.mqtt.server.session.data.ClientSession;
import com.free.mqtt.server.session.data.StoredMessage;
import com.free.mqtt.server.subscriptions.ISubscriptionsDirectory;
import com.free.mqtt.server.subscriptions.data.Topic;
import com.free.mqtt.server.utils.MqttBrokerUtil;
import io.netty.handler.codec.mqtt.MqttPublishMessage;

public class Qos0Processor extends QosProcessor {

    public Qos0Processor(Interceptor interceptor, IAuthorizator authorizator, ISubscriptionsDirectory subscriptionsDirectory, SessionRepository sessionsRepository) {
        super(interceptor, authorizator, subscriptionsDirectory, sessionsRepository);
    }

    public void receivedQos0Msg(ClientSession clientSession, MqttPublishMessage msg) throws Exception {

        Topic topic = new Topic(msg.variableHeader().topicName());

        if( !authorizator.canWrite(topic, clientSession.getUserName(), clientSession.getClientId()) ){
            logger.error("MQTT client is not authorized to publish on topic. CId={}, topic={}", clientSession.getClientId(), topic);
            return;
        }

        StoredMessage toStoreMsg = MqttBrokerUtil.asStoredMessage(msg, clientSession.getClientId());

        //publish to subscribes
        publish2Subscribers(clientSession.remoteAddr(), toStoreMsg, topic);
    }
}
