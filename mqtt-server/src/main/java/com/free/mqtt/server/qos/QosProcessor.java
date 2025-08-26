package com.free.mqtt.server.qos;

import com.free.mqtt.server.auth.IAuthorizator;
import com.free.mqtt.server.event.MqttFlushCacheEvent;
import com.free.mqtt.server.interceptor.Interceptor;
import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.session.SessionRepository;
import com.free.mqtt.server.session.data.ClientSession;
import com.free.mqtt.server.session.data.StoredMessage;
import com.free.mqtt.server.subscriptions.ISubscriptionsDirectory;
import com.free.mqtt.server.subscriptions.data.Subscription;
import com.free.mqtt.server.subscriptions.data.Topic;
import com.free.mqtt.server.utils.MsgUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class QosProcessor {

    protected static final Logger logger = LoggerFactory.getLogger(QosProcessor.class);

    protected Interceptor interceptor;

    protected IAuthorizator authorizator;

    protected ISubscriptionsDirectory subscriptionsDirectory;

    protected SessionRepository sessionsRepository;

    public QosProcessor(Interceptor interceptor, IAuthorizator authorizator, ISubscriptionsDirectory subscriptionsDirectory, SessionRepository sessionsRepository) {
        this.interceptor = interceptor;
        this.authorizator = authorizator;
        this.subscriptionsDirectory = subscriptionsDirectory;
        this.sessionsRepository = sessionsRepository;
    }

    public void publish2Subscribers(String ip, StoredMessage pubMsg, Topic topic) throws Exception {

        // 如果客户端的消息被拦截器滤除,则不用转发
       if( interceptor.filterMsg(ip, pubMsg) ){
            return;
        }

        List<Subscription> topicMatchingSubscriptions = subscriptionsDirectory.matches(topic);
        if (null == topicMatchingSubscriptions || topicMatchingSubscriptions.size() <= 0) {
            logger.error("没有订阅者  topic:{}, msgUUID={}, msg={}", topic, pubMsg.getMsgUUID(), MsgUtil.toMsg(pubMsg.getPayload()));
            return;
        }

        // 向所有的订阅者发送消息,这里要处理异常的,避免部分订阅者收不到消息
        for (final Subscription sub : topicMatchingSubscriptions) {
            ClientSession clientSession = sessionsRepository.getSession(sub.getClientId());
            if(null == clientSession){
                logger.error("存在严重bug, session已经失效，但订阅的topic依然保留, msgUUID={}, sub:{}", pubMsg.getMsgUUID(), sub);
                continue;
            }

            //logger.info("将推送的消息放入队列clientId:" + sub.getClientId() + " BusinessMsgId:" + pubMsg.getBusinessMsgId());

            // 将消息放入队列中
            clientSession.addSendMsg(pubMsg);

            // 对于需要推送消息的client，添加到事件队列
            MqttNettyChannel currentChannel = sessionsRepository.getSession(sub.getClientId()).getChannel();
            if(null != currentChannel && false == currentChannel.isHaveSendEvent() ){
                currentChannel.fireEvent(new MqttFlushCacheEvent());
            }
        }
    }

    public ISubscriptionsDirectory getSubscriptionsDirectory() {
        return subscriptionsDirectory;
    }

    public void setSubscriptionsDirectory(ISubscriptionsDirectory subscriptionsDirectory) {
        this.subscriptionsDirectory = subscriptionsDirectory;
    }

    public SessionRepository getSessionsRepository() {
        return sessionsRepository;
    }

    public void setSessionsRepository(SessionRepository sessionsRepository) {
        this.sessionsRepository = sessionsRepository;
    }
}
