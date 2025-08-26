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
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Qos1Processor extends QosProcessor {

    private static final Logger logger = LoggerFactory.getLogger(Qos1Processor.class);

    public Qos1Processor(Interceptor interceptor, IAuthorizator authorizator, ISubscriptionsDirectory subscriptionsDirectory, SessionRepository sessionsRepository) {
        super(interceptor, authorizator, subscriptionsDirectory, sessionsRepository);
    }

    /**
     * broker作为接收端
     * @param
     * @param clientSession
     * @param msg
     * @throws Exception
     */
    public void receivedQos1Msg(ClientSession clientSession, MqttPublishMessage msg) throws Exception {
        Topic topic = new Topic(msg.variableHeader().topicName());

        int messageID = msg.variableHeader().packetId();


        if( !authorizator.canWrite(topic, clientSession.getUserName(), clientSession.getClientId()) ){
            logger.error("MQTT client is not authorized to publish on topic. CId={}, topic={}", clientSession.getClientId(), topic);
            return;
        }

        //向客户端发送确认消息
        clientSession.writeAndFlush(MqttBrokerUtil.mqttMqttPubAckMessage(messageID));

        StoredMessage toStoreMsg = MqttBrokerUtil.asStoredMessage(msg, clientSession.getClientId());

        //publish to subscribes
        publish2Subscribers(clientSession.remoteAddr(), toStoreMsg, topic);
    }

    /**
     * broker作为发送端
     * @param clientSession
     * @param msg
     */
    public void receivedPubAck(ClientSession clientSession, MqttPubAckMessage msg) {
        try{
            int msgId = msg.variableHeader().messageId();


            StoredMessage inflightMsg = clientSession.removeHaveSendMsg(msgId);

            if (null == inflightMsg) {
                return;
            }
            interceptor.cancelPushMsgTimeTask(clientSession.getClientId(), msgId);

//			logger.info("推送的消息 响应clientId:{}, messageId:{}", inflightMsg.getClientID(), inflightMsg.getMessageId());

            //通知拦截器，客户端已经收到消息了
            if(null != inflightMsg.getBusinessMsgId()){
                interceptor.notifySendMsgOk(clientSession.getClientId(), inflightMsg.getTopic(), inflightMsg.getQos(), inflightMsg.getBusinessMsgId());
            }
        }catch(Exception e){

        }finally{
            clientSession.fireEvent(new MqttFlushCacheEvent());
        }
    }
}
