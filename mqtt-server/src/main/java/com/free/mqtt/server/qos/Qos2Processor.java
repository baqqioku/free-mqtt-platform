package com.free.mqtt.server.qos;

import com.free.mqtt.server.auth.IAuthorizator;
import com.free.mqtt.server.event.MqttFlushCacheEvent;
import com.free.mqtt.server.interceptor.Interceptor;
import com.free.mqtt.server.session.SessionRepository;
import com.free.mqtt.server.session.data.ClientSession;
import com.free.mqtt.server.session.data.StoredMessage;
import com.free.mqtt.server.subscriptions.ISubscriptionsDirectory;
import com.free.mqtt.server.subscriptions.data.Topic;
import com.free.mqtt.server.utils.MqttBrokerUtil;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Qos2Processor extends QosProcessor {

    private static final Logger logger = LoggerFactory.getLogger(Qos2Processor.class);

    public Qos2Processor(Interceptor interceptor, IAuthorizator authorizator, ISubscriptionsDirectory subscriptionsDirectory, SessionRepository sessionsRepository) {
        super(interceptor, authorizator, subscriptionsDirectory, sessionsRepository);
    }

    /**
     * broker作为接收者
     * @param
     * @param msg
     * @throws Exception
     */
    public void receivedQos2Msg(ClientSession clientSession, MqttPublishMessage msg) throws Exception {
        Topic topic = new Topic(msg.variableHeader().topicName());

        int messageID = msg.variableHeader().packetId();


        if (!authorizator.canWrite(topic, clientSession.getUserName(), clientSession.getClientId())) {
            logger.error("MQTT client is not authorized to publish on topic. CId={}, topic={}", clientSession.getClientId(), topic);
            return;
        }

        StoredMessage toStoreMsg = MqttBrokerUtil.asStoredMessage(msg, clientSession.getClientId());

        clientSession.inBoundMsgRelWaiting(messageID, toStoreMsg);

        sendPubRec(clientSession, messageID);
    }


    /**
     * broker作为接收者
     * @param
     * @param msg
     * @throws Exception
     */
    public void receivedPubRel(ClientSession clientSession, MqttMessage msg) throws Exception {
        int messageID = MqttBrokerUtil.messageId(msg);

        StoredMessage storedMessage = clientSession.inBoundMsgRelOk(messageID);
        if (null == storedMessage) {
            return;
        }

        sendPubComp(clientSession, messageID);

        // 推送到订阅者
        publish2Subscribers(clientSession.remoteAddr(), storedMessage, new Topic(storedMessage.getTopic()));
    }


    /**
     * broker作为发送者
     * @param
     * @param msg
     * @throws Exception
     */
    public void receivedPubRec(ClientSession clientSession, MqttMessage msg) throws Exception {
        int messageID = MqttBrokerUtil.messageId(msg);

        sendPubRel(clientSession, messageID);
    }



    /**
     * broker作为发送者
     * @param
     * @param msg
     */
    public void receivedPubComp(ClientSession clientSession, MqttMessage msg) {
        try{
            int msgId = MqttBrokerUtil.messageId(msg);
            StoredMessage storedMessage = clientSession.removeHaveSendMsg(msgId);
            if (null == storedMessage) {
                return;
            }

            interceptor.cancelPushMsgTimeTask(clientSession.getClientId(), msgId);

            //向系统监听器发送事件
            if(null != storedMessage.getBusinessMsgId()){
                interceptor.notifySendMsgOk(clientSession.getClientId(), storedMessage.getTopic(), storedMessage.getQos(), storedMessage.getBusinessMsgId());
            }
        }catch(Exception e){

        }finally{
            clientSession.fireEvent(new MqttFlushCacheEvent());
        }

    }


    public void sendPubRel(ClientSession clientSession, int messageID) throws Exception {
        clientSession.writeAndFlush(MqttBrokerUtil.pubRelMqttMessage(messageID));
    }

    public void sendPubRec(ClientSession clientSession, int messageID) throws Exception {
        clientSession.writeAndFlush(MqttBrokerUtil.pubRecMqttMessage(messageID));
    }

    public void sendPubComp(ClientSession clientSession, int messageID) throws Exception {
        clientSession.writeAndFlush(MqttBrokerUtil.pubCompMqttMessage(messageID));
    }
}
