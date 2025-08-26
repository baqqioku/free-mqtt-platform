package com.free.mqtt.server.qos;


import com.free.common.utils.StringUtil;
import com.free.mqtt.server.auth.AuthChannel;
import com.free.mqtt.server.auth.DefaultAuthorizator;
import com.free.mqtt.server.auth.IAuthorizator;
import com.free.mqtt.server.event.MqttFlushCacheEvent;
import com.free.mqtt.server.interceptor.Interceptor;
import com.free.mqtt.server.interceptor.RetryPushTimerTask;
import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.session.SessionRepository;
import com.free.mqtt.server.session.data.ClientSession;
import com.free.mqtt.server.session.data.StoredMessage;
import com.free.mqtt.server.subscriptions.ISubscriptionsDirectory;
import com.free.mqtt.server.subscriptions.data.Subscription;
import com.free.mqtt.server.subscriptions.data.Topic;
import com.free.mqtt.server.utils.MqttBrokerUtil;
import com.free.mqtt.server.utils.MsgUtil;
import com.free.mqtt.server.utils.PerfUtil;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ProtocolProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolProcessor.class);

    private Interceptor interceptor;

    private IAuthorizator authorizator;

    private SessionRepository sessionsRepository;

    private ISubscriptionsDirectory subscriptionsDirectory;

    private Qos0Processor qos0Processor;

    private Qos1Processor qos1Processor;

    private Qos2Processor qos2Processor;

    public ProtocolProcessor(SessionRepository sessionRepository, Interceptor interceptor,ISubscriptionsDirectory subscriptionsDirectory) {
        this.sessionsRepository = sessionRepository;
        this.subscriptionsDirectory = subscriptionsDirectory;

        this.interceptor = interceptor;
        this.authorizator = new DefaultAuthorizator();

        qos0Processor = new Qos0Processor(interceptor,authorizator,subscriptionsDirectory, sessionsRepository);
        qos1Processor = new Qos1Processor(interceptor,authorizator,subscriptionsDirectory, sessionsRepository);
        qos2Processor = new Qos2Processor(interceptor,authorizator,subscriptionsDirectory, sessionsRepository);

    }

    public void auth(AuthChannel authChannel){
        try{
            if( !authChannel.getAuthChannel().isActive() ){
                logger.error("授权通道已经关闭了   clientId:{}", authChannel.getAuthChannel().getClientId());
                return;
            }

            if(false == authChannel.getAuthChannel().isAuth()){

                logger.error("通道授权失败   clientId:{}", authChannel.getAuthChannel().getClientId());

                MqttConnAckMessage connAckMessage = MqttBrokerUtil.mqttConnAckMessage(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD, false);
                authChannel.getAuthChannel().writeAndFlush(connAckMessage);
            }else{
                ClientSession clientSession = sessionsRepository.createClientSession(authChannel.getAuthChannel(), authChannel.getAuthChannel().getClientId());

               /* boolean cleanSession = msg.variableHeader().isCleanSession();
                clientSession.setCleanSession(cleanSession);
                if (msg.variableHeader().isWillFlag()) {
                    WillMessage will = new WillMessage();
                    will.topic = msg.payload().willTopic();
                    will.message = new String(msg.payload().willMessageInBytes());
                    will.qos = msg.variableHeader().willQos();
                    clientSession.setWillMessage(will);
                }*/

                MqttConnAckMessage connAckMessage = MqttBrokerUtil.mqttConnAckMessage(MqttConnectReturnCode.CONNECTION_ACCEPTED, false);
                authChannel.getAuthChannel().writeAndFlush(connAckMessage);

                //自动定义topic
                //if( !authChannel.getAuthChannel().isForCluster() ){
                    String autoSub = interceptor.autoSub(authChannel.getClientId());
                    if(null != autoSub){

                        logger.info("自动订阅topic:{},ClientId:{}", autoSub, authChannel.getClientId());

                        Topic topic = new Topic(autoSub);

                        Subscription newSubscription = new Subscription(authChannel.getClientId(), MqttQoS.AT_LEAST_ONCE, topic);

                        clientSession.subscribe(topic, newSubscription);

                        subscriptionsDirectory.addSubscription(newSubscription);
                    }
                //}

                //通道建立连接，需要推送消息
                authChannel.getAuthChannel().fireEvent(new MqttFlushCacheEvent());
            }
        }catch(Exception e){
            logger.error("授权失败", e);
        }
    }

    public void sendMsg( String topic, byte[] payload, MqttQoS qos, Integer businessMsgId, long createTime, long ttl, String msgUUID) throws Exception{
        StoredMessage toStoreMsg = new StoredMessage(payload, qos, topic, "system", businessMsgId, ttl, msgUUID);

        Topic topicObj = new Topic(topic);

        switch (qos) {
            case AT_MOST_ONCE:
                qos0Processor.publish2Subscribers(null, toStoreMsg, topicObj);
                break;
            case AT_LEAST_ONCE:
                qos1Processor.publish2Subscribers(null, toStoreMsg, topicObj);
                break;
            case EXACTLY_ONCE:
                qos2Processor.publish2Subscribers(null, toStoreMsg, topicObj);
                break;
            default:
                logger.error("Unknown QoS-Type:{}");
                break;
        }
    }


    public void processMsg(MqttNettyChannel channel, MqttMessage msg) {
        try {
            MqttMessageType messageType = msg.fixedHeader().messageType();
            if (messageType.CONNECT == messageType) {
                this.processConnect(channel, (MqttConnectMessage) msg);
                return;
            }

            if(false == channel.isAuth() ){
                logger.error("通道还未授权通过,clientId:{}", channel.getClientId());
                channel.close();
                return;
            }

            ClientSession clientSession = sessionsRepository.getSession(channel.getClientId());
            if (null == clientSession) {
                logger.error("通道异常,clientId:{}", channel.getClientId());
                return;
            }

            clientSession.touch();

            logger.info("clientId={},  mesageType={}", channel.getClientId(), messageType);
            switch (messageType) {
                case DISCONNECT:
                    logger.info("客户端主动断开连接,clientId:{}", channel.getClientId());
                    this.processDisconnect(channel);
                    break;

                case SUBSCRIBE:
                    this.processSubscribe(clientSession, (MqttSubscribeMessage) msg);
                    break;

                case UNSUBSCRIBE:
                    this.processUnsubscribe(clientSession, (MqttUnsubscribeMessage) msg);
                    break;

                case PUBLISH:
                    this.processPublish(clientSession, (MqttPublishMessage) msg);
                    break;

                case PUBREL:
                    this.processPubRel(clientSession, (MqttMessage) msg);
                    break;

                case PUBREC:
                    this.processPubRec(clientSession, (MqttMessage) msg);
                    break;

                case PUBCOMP:
                    this.processPubComp(clientSession, (MqttMessage) msg);
                    break;

                case PUBACK:
                    this.processPubAck(clientSession, (MqttPubAckMessage) msg);
                    break;

                case PINGREQ:
                    this.processPing(clientSession);
                    break;

                default:
                    logger.error("Unkonwn MessageType:{}", messageType);
            }

        } catch (Throwable ex) {
            logger.error("Exception was caught while processing MQTT message", ex);
        }
    }


    public void pushMsg(MqttNettyChannel currentChannel) {

        ClientSession clientSession = sessionsRepository.getSession(currentChannel.getClientId());
        if( !clientSession.isSameChannel(currentChannel) ){
            logger.error("已经不是同一个通道了   clientId:{}", currentChannel.getClientId());
            return;
        }

        //有一个消息正在推送中，需要客户端接收完成才能够推送下一个
        //需要判断该消息推送的时长
        if(clientSession.inSendMsgSize() > 0){
            ClientSession.QueueMsg queueMsg = clientSession.peekNextSendMsg();
            if(null == queueMsg){
                //logger.info("消息已经推送完毕,clientId:{}",clientSession.getClientID());
                currentChannel.setHaveSendEvent(false);
                return;
            }

            //进入重试逻辑
            long span = System.currentTimeMillis() - queueMsg.getLastPushTime();
            if (span >= PerfUtil.getSendWaitAckTime()) {
                if (queueMsg.getPushCount() <= PerfUtil.getRetrySendCount()) {
                    queueMsg.incrPushCount(1);
                    queueMsg.setLastPushTime(System.currentTimeMillis());

                    //再次发送本条消息
                    logger.info("尝试再次发送消息, msgUUID={}, clientId={}, msgId={}, bizMsgId={}, pushCount={}", queueMsg.getMsg().getMsgUUID(), clientSession.getClientId(), queueMsg.getMsgId(), queueMsg.getMsg().getBusinessMsgId(), queueMsg.getPushCount());
                } else {

                    logger.info("发送消息失败关闭通道, msgUUID={}, clientId={}, msgId={}, bizMsgId={}, pushCount={}", queueMsg.getMsg().getMsgUUID(), clientSession.getClientId(), queueMsg.getMsgId(), queueMsg.getMsg().getBusinessMsgId(), queueMsg.getPushCount());

                    //关闭通道
                    clientSession.close();
                    return;
                }
            } else {
                // 等待客户端返回ack
                // logger.info("有消息正在推送中,等待ack,clientId:{}, msgId={}, bizMsgId={}, inSendMsgSize={}", clientSession.getClientID(), queueMsg.getMsgId(), queueMsg.getMsg().getBusinessMsgId(), clientSession.inSendMsgSize());
                return;
            }
        }

        ClientSession.QueueMsg queueMsg = clientSession.peekNextSendMsg();
        if(null == queueMsg){
            //logger.info("消息已经推送完毕,clientId:{}",clientSession.getClientID());
            currentChannel.setHaveSendEvent(false);
            return;
        }

        StoredMessage nextMsg = queueMsg.getMsg();

        try{
//			logger.info("推送的消息为 clientId:{},messageId:{}", nextMsg.getClientID(), queueMsg.getMsgId());
            //这个是业务推送
            if( null != nextMsg.getBusinessMsgId() ){
                if( !interceptor.checkTtl(nextMsg.getCreateTime(), nextMsg.getTtl()) ){
                    clientSession.removeHaveSendMsg(queueMsg.getMsgId());

                    interceptor.cancelPushMsgTimeTask(currentChannel.getClientId(), queueMsg.getMsgId());

                    //通知拦截器，客户端已经收到消息了
                    interceptor.notifySendMsgOk(currentChannel.getClientId(), nextMsg.getTopic(), nextMsg.getQos(), nextMsg.getBusinessMsgId());
                    return;
                }
            }

            currentChannel.writeAndFlush( MqttBrokerUtil.mqttPublishMessage(queueMsg.getMsgId(), nextMsg) );

            nextMsg.setSendTime(System.currentTimeMillis());

            if (PerfUtil.isSendSlow(nextMsg.getCreateTime())) {
                long usedTime = System.currentTimeMillis() - nextMsg.getCreateTime();
                logger.info("消息发送到app慢, msgUUID={}, usedTime={}, slowReqLevel={}, msg={}",
                        nextMsg.getMsgUUID(), usedTime, MsgUtil.toMsg(nextMsg.getPayload()));
            }

            //qos0      是没有应答消息的
            if( MqttQoS.AT_MOST_ONCE == nextMsg.getQos() ){
                clientSession.removeHaveSendMsg(queueMsg.getMsgId());

                //通知拦截器，客户端已经收到消息了
                if(null != nextMsg.getBusinessMsgId()){
                    interceptor.notifySendMsgOk(currentChannel.getClientId(), nextMsg.getTopic(), nextMsg.getQos(), nextMsg.getBusinessMsgId());
                }
            } else {
                //构建一个重发定时器
                RetryPushTimerTask task = new RetryPushTimerTask(clientSession, queueMsg, PerfUtil.getRetrySendDelay());
                interceptor.startPushMsgTimeTask(task);
            }
        }catch(Exception e){
            logger.error("消息发布异常",e);
        }finally{
            currentChannel.fireEvent(new MqttFlushCacheEvent());
        }

    }

    private void processConnect(MqttNettyChannel channel, MqttConnectMessage msg) throws Exception {
        MqttConnectReturnCode returnCode = MqttConnectReturnCode.CONNECTION_ACCEPTED;

        String clientId = null;

        boolean loop = true;
        while(loop){
            loop = false;

            clientId = msg.payload().clientIdentifier();
            if( StringUtil.isNullOrEmpty(clientId) ){
                logger.error("clientId为空");

                returnCode = MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
                return;
            }

            logger.info("客户端建立连接,clientId:{}", clientId);

            //通道关联clientId
            channel.setClientId(clientId);

            //mqtt支持的版本有限
            int currentVersion = msg.variableHeader().version();
            if (currentVersion != MqttVersion.MQTT_3_1.protocolLevel() && currentVersion != MqttVersion.MQTT_3_1_1.protocolLevel()) {
                logger.error("mqtt协议版本不支持, 目前仅支持mqtt_3_1和mqtt_3_1_1 clientId={}", clientId);

                returnCode = MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION;
                break;
            }

            //校验用户名和密码
            if( !msg.variableHeader().hasUserName() || !msg.variableHeader().hasPassword() ){
                logger.error("用户名和密码不能够为空    clientId:{}", clientId);

                returnCode = MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;
                break;
            }
        }


        //连接请求认证不通过
        if(returnCode != MqttConnectReturnCode.CONNECTION_ACCEPTED){
            MqttConnAckMessage connAckMessage = MqttBrokerUtil.mqttConnAckMessage(returnCode, false);

            channel.writeAndFlush(connAckMessage);
            channel.close();
            return;
        }

        AuthChannel authChannel = new AuthChannel(clientId, msg.payload().userName(), msg.payload().password(), channel,this);
        interceptor.checkValid(authChannel);

    }

    private void processDisconnect(MqttNettyChannel channel) {
        sessionsRepository.removeClinetSession(channel, channel.getClientId());
    }

    /**
     * 服务端publish qos1消息到客户端，客户端给的响应报文
     */
    public void processPubAck(ClientSession clientSession, MqttPubAckMessage msg) {
        qos1Processor.receivedPubAck(clientSession, msg);
    }

    /**
     * 客户端publish消息到服务端
     *
     * @throws Exception
     */
    public void processPublish(ClientSession clientSession, MqttPublishMessage msg) throws Exception {
        final MqttQoS qos = msg.fixedHeader().qosLevel();

        switch (qos) {
            case AT_MOST_ONCE:
                qos0Processor.receivedQos0Msg(clientSession, msg);
                break;
            case AT_LEAST_ONCE:
                qos1Processor.receivedQos1Msg(clientSession, msg);
                break;
            case EXACTLY_ONCE:
                qos2Processor.receivedQos2Msg(clientSession, msg);
                break;
            default:
                logger.error("Unknown QoS-Type:{}", qos);
                break;
        }
    }

    /**
     * 客户端返回确认消息，确认qos 2的消息已经收到
     *
     * @throws Exception
     */
    public void processPubRec(ClientSession clientSession, MqttMessage msg) throws Exception {
        qos2Processor.receivedPubRec(clientSession, msg);
    }

    /**
     * 表示该消息双方已经确认，可以确认消息可以消费
     *
     * @throws Exception
     */
    public void processPubRel(ClientSession clientSession, MqttMessage msg) throws Exception {
        qos2Processor.receivedPubRel(clientSession, msg);
    }

    public void processPubComp(ClientSession clientSession, MqttMessage msg) {
        qos2Processor.receivedPubComp(clientSession, msg);
    }

    public void processSubscribe(ClientSession clientSession, MqttSubscribeMessage msg) throws Exception {
        int messageID = MqttBrokerUtil.messageId(msg);

        List<MqttTopicSubscription> topicFilters = new ArrayList<>();
        for (MqttTopicSubscription req : msg.payload().topicSubscriptions()) {
            Topic topic = new Topic(req.topicName());


            if (topic.isValid()) {
                logger.info("订阅clientId:{},有效topic:{}", clientSession.getClientId(), topic);

                topicFilters.add(new MqttTopicSubscription(req.topicName(), req.qualityOfService()));

                Subscription newSubscription = new Subscription(clientSession.getClientId(), req.qualityOfService(), topic);

                clientSession.subscribe(topic, newSubscription);

                subscriptionsDirectory.addSubscription(newSubscription);
            } else {

                logger.error("订阅clietnId:{},无效topic:{}", clientSession.getClientId(), topic);

                topicFilters.add(new MqttTopicSubscription(req.topicName(), MqttQoS.FAILURE));
            }

        }

        MqttSubAckMessage mqttSubAckMessage = MqttBrokerUtil.mqttSubAckMessage(topicFilters, messageID);
        clientSession.writeAndFlush(mqttSubAckMessage);
    }

    public void processUnsubscribe(ClientSession clientSession, MqttUnsubscribeMessage msg) throws Exception {
        int messageID = MqttBrokerUtil.messageId(msg);

        List<Topic> topicFilters = new ArrayList<Topic>();

        List<String> topics = msg.payload().topics();
        for (String t : topics) {
            Topic topic = new Topic(t);

            if (!topic.isValid()) {
                logger.error("取消订阅的topic:{}有问题", t);
                return;
            }

            topicFilters.add(topic);
        }


        for (Topic topic : topicFilters) {
            logger.info("取消订阅clientId:{}, topic有效:{}", clientSession.getClientId(), topic);

            Subscription unSubscription = new Subscription(clientSession.getClientId(), topic);

            clientSession.unsubscribe(topic);
            subscriptionsDirectory.removeSubscription(unSubscription);
        }


        MqttUnsubAckMessage mqttUnsubAckMessage = MqttBrokerUtil.mqttUnsubAckMessage(messageID);
        clientSession.writeAndFlush(mqttUnsubAckMessage);
    }

    public void processPing(ClientSession clientSession) throws Exception {
        clientSession.writeAndFlush(MqttBrokerUtil.getPingMqttMessage());
    }

    /**
     * 通道因为网络原因断开连接，session依然保留
     *
     * @param channel
     */
    public void processConnectionLost(MqttNettyChannel channel) {
        logger.info("通道断开连接,clientId:{}", channel.getClientId());

        processDisconnect(channel);
    }


    public void processConnectionException(MqttNettyChannel channel) {

        logger.error("通道异常,clientId:{}", channel.getClientId());

        processDisconnect(channel);
    }


}
