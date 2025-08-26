package com.free.mqtt.server.interceptor;

import com.free.mqtt.server.event.MqttFlushCacheEvent;
import com.free.mqtt.server.session.data.ClientSession;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryPushTimerTask implements TimerTask {

    private static final Logger logger = LoggerFactory.getLogger(RetryPushTimerTask.class);

    private String taskId;

    private ClientSession clientSession;

    private ClientSession.QueueMsg queueMsg;

    private long delay;

    private volatile boolean cancel = false;

    public RetryPushTimerTask( ClientSession clientSession, ClientSession.QueueMsg queueMsg, long delay) {
        this.taskId = genTaskId(clientSession.getClientId(), queueMsg.getMsgId());;
        this.clientSession = clientSession;
        this.queueMsg = queueMsg;
        this.delay = delay;
    }


    @Override
    public void run(Timeout timeout) throws Exception {
        if(cancel){
            return;
        }
        clientSession.fireEvent(new MqttFlushCacheEvent());
    }

    public static String genTaskId(String clientId, int msgId) {
        if (StringUtil.isNullOrEmpty(clientId)) {
            return null;
        }

        return clientId + "_" + msgId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public ClientSession getClientSession() {
        return clientSession;
    }

    public void setClientSession(ClientSession clientSession) {
        this.clientSession = clientSession;
    }

    public ClientSession.QueueMsg getQueueMsg() {
        return queueMsg;
    }

    public void setQueueMsg(ClientSession.QueueMsg queueMsg) {
        this.queueMsg = queueMsg;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public boolean isCancel() {
        return cancel;
    }

    public void setCancel(boolean cancel) {
        this.cancel = cancel;
    }
}
