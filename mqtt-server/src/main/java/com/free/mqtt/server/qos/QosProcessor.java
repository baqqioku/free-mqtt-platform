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
import com.free.common.constant.MqttConstant;
import io.netty.handler.codec.mqtt.MqttQoS;
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

        if (pubMsg != null && pubMsg.isRetained()) {
            RetainedRepository.store(pubMsg);
        }

        long userId = parseUserIdFromTopic(topic.toString());
        if (userId > 0 && pubMsg != null && pubMsg.getQos() != null && MqttQoS.AT_MOST_ONCE != pubMsg.getQos()) {
            try {
                interceptor.storeOfflineMessage(userId, pubMsg);
            } catch (Exception ignored) {
            }
        }

        List<Subscription> topicMatchingSubscriptions = subscriptionsDirectory.matches(topic);
        if (null == topicMatchingSubscriptions || topicMatchingSubscriptions.size() <= 0) {
            logger.error("没有订阅者  topic:{}, msgUUID={}, msg={}", topic, pubMsg.getMsgUUID(), MsgUtil.toMsg(pubMsg.getPayload()));
            if (userId > 0) {
                try {
                    interceptor.storeOfflineMessage(userId, pubMsg);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        // 向所有的订阅者发送消息,这里要处理异常的,避免部分订阅者收不到消息
        for (final Subscription sub : topicMatchingSubscriptions) {
            ClientSession clientSession = sessionsRepository.getSession(sub.getClientId());
            if(null == clientSession){
                // FIX: 当Session失效时，清理孤立的订阅，防止内存泄漏
                logger.warn("Session已过期，清理孤立订阅: clientId={}, topic={}", sub.getClientId(), sub.getTopicFilter());
                subscriptionsDirectory.removeSubscription(sub);
                continue;
            }

            // 将消息放入队列中
            clientSession.addSendMsg(pubMsg);

            // 对于需要推送消息的client，添加到事件队列
            MqttNettyChannel currentChannel = clientSession.getChannel();
            if(null != currentChannel && !currentChannel.isHaveSendEvent()){
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

    private long parseUserIdFromTopic(String topic) {
        if (topic == null) {
            return -1;
        }
        if (!topic.startsWith(MqttConstant.brokerToClientTopic)) {
            return -1;
        }
        String tail = topic.substring(MqttConstant.brokerToClientTopic.length());
        if (tail.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(tail);
        } catch (Exception e) {
            return -1;
        }
    }
}
