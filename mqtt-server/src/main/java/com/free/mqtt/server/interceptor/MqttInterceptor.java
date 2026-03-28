package com.free.mqtt.server.interceptor;

import com.free.mqtt.MqttServer;
import com.free.mqtt.server.auth.AuthChannel;
import com.free.mqtt.server.event.MqttAuthEvent;
import com.free.mqtt.server.event.MqttDisconnectEvent;
import com.free.mqtt.server.event.MqttSendMsgEndEvent;
import com.free.mqtt.server.session.data.StoredMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.HashedWheelTimer;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MqttInterceptor implements Interceptor{

    private Logger logger = LoggerFactory.getLogger(MqttInterceptor.class);

    private String filterTopic;

    private MqttServer mqttServer;

    private HashedWheelTimer hashedWheelTimer = new HashedWheelTimer(1, TimeUnit.SECONDS,60);

    private Map<String,RetryPushTimerTask> timeTaskMapInfo = new ConcurrentHashMap<>();

    public MqttInterceptor(String filterTopic) {
        this.filterTopic = filterTopic;
    }

    public MqttInterceptor(MqttServer mqttServer, String filterTopic) {
        this.mqttServer = mqttServer;

        this.filterTopic = filterTopic;
    }

    @Override
    public boolean filterMsg(String ip, StoredMessage pubMsg) {
        return false;
    }

    @Override
    public void notifySendMsgOk(String clientId, String topic, MqttQoS qos, Integer pushMessageId) {
        MqttSendMsgEndEvent mqttSendMsgEndEvent = new MqttSendMsgEndEvent(clientId, topic, qos, pushMessageId);

        mqttServer.getMqttMsgProcessThread().submit(mqttSendMsgEndEvent);
    }

    @Override
    public void notifyDisconnect(String ip, String clientId) {
        MqttDisconnectEvent mqttDisconnectEvent = new MqttDisconnectEvent(clientId, ip);

        mqttServer.getMqttMsgProcessThread().submit(mqttDisconnectEvent);
    }

    @Override
    public void heartbeat(String clientId) {

    }

    @Override
    public void checkValid(AuthChannel authChannel) {
        //@todo 权限校验逻辑未实现
        MqttAuthEvent mqttAuthEvent = new MqttAuthEvent(authChannel.getClientId(),authChannel);
        mqttServer.getMqttMsgProcessThread().submit(mqttAuthEvent);
    }

    @Override
    public boolean checkTtl(long createTime, long ttl) {
        if (ttl <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long ttlMillis = ttl * 1000L;
        return now - createTime <= ttlMillis;
    }

    @Override
    public String autoSub(String clientId) {
        return mqttServer.getMqttMsgListener().autoSub(clientId);
    }

    @Override
    public void storeOfflineMessage(long userId, StoredMessage msg) {
        mqttServer.getMqttMsgListener().storeOfflineMessage(userId, msg);
    }

    @Override
    public List<StoredMessage> popOfflineMessages(String clientId, int maxCount) {
        return mqttServer.getMqttMsgListener().popOfflineMessages(clientId, maxCount);
    }

    @Override
    public void markInflight(long userId, String msgUUID, long sendAtSec) {
        mqttServer.getMqttMsgListener().markInflight(userId, msgUUID, sendAtSec);
    }

    @Override
    public void ackMessage(long userId, String msgUUID) {
        mqttServer.getMqttMsgListener().ackMessage(userId, msgUUID);
    }

    @Override
    public void startPushMsgTimeTask(RetryPushTimerTask task) {

        String taskId = task.getTaskId();
        if(StringUtil.isNullOrEmpty(taskId)){
            logger.info("taskId是null，启用重试定时任务, msgUUID={}, clientId={}, msgId={}, businessMsgId={}, topic={}",
                    task.getQueueMsg().getMsg().getMsgUUID(),
                    task.getClientSession().getClientId(),
                    task.getQueueMsg().getMsgId(),
                    task.getQueueMsg().getMsg().getBusinessMsgId(),
                    task.getQueueMsg().getMsg().getTopic());
            hashedWheelTimer.newTimeout(task,task.getDelay(),TimeUnit.SECONDS);
            return;
        }
        timeTaskMapInfo.put(task.getTaskId(),task);
        hashedWheelTimer.newTimeout(task,task.getDelay(),TimeUnit.SECONDS);

    }

    @Override
    public void cancelPushMsgTimeTask(String clientId, int msgId) {
        String taskId= RetryPushTimerTask.genTaskId(clientId,msgId);
        if(!StringUtil.isNullOrEmpty(taskId)){
            RetryPushTimerTask  task = timeTaskMapInfo.get(taskId);
            if (task != null) {
                task.setCancel(true);
                timeTaskMapInfo.remove(taskId);
            }
        }else {
            logger.info("taskId是null，不取消定时任务, clientId={}, msgId={}, topic={}", clientId, msgId);
        }
    }
}
