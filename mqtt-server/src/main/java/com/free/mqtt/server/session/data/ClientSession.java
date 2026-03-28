package com.free.mqtt.server.session.data;

import com.free.common.constant.MqttConstant;
import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.subscriptions.ISubscriptionsDirectory;
import com.free.mqtt.server.subscriptions.data.Subscription;
import com.free.mqtt.server.subscriptions.data.Topic;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSession implements Serializable {

    private String clientId;  // 客户端唯一 ID
    private String userId;   //用户id 可选
    private String userName;

    private long connectTime;//本次连接时间
    private long lastActiveAt; //最后活跃的时间

    private boolean cleanSession;//是否清除会话

    private int keepAliveSeconds; //保持链接时间

    private WillMessage willMessage;//遗言消息

    private long lastPacketReciveTime;

    private Map<Topic, Subscription> subscriptions = new ConcurrentHashMap<>();

    private ISubscriptionsDirectory subscriptionsDirectory;

    private Queue<QueueMsg> sendQueue = new LinkedBlockingQueue<QueueMsg>();


    /**
     * 🚀 QoS1 (至少一次)
     * Broker → Client
     * Broker                           Client
     *   |---- PUBLISH(msgId=101) ------>|
     *   |   (保存到 OutboundFlightZone) |
     *   |                               |
     *   |<--- PUBACK(101) ---------------|
     *   |   (从 OutboundFlightZone移除) |
     *
     *
     * 👉 用到：OutboundFlightZone
     *
     * 因为 broker 发出去的消息，必须等 client 的 PUBACK 才算确认。
     *
     * 如果没等到，就重发（你看到的 retry 逻辑就是在这儿起作用的）。
     *
     * 🚀 QoS2 (正好一次，最复杂)
     * Client → Broker
     * Client                           Broker
     *   |---- PUBLISH(msgId=200) ------>|
     *   |                               | (放入 InboundFlightZone)
     *   |<--- PUBREC(200) ---------------|
     *   |                               |
     *   |---- PUBREL(200) -------------->|
     *   |                               | (从 InboundFlightZone移除)
     *   |<--- PUBCOMP(200) --------------|
     *
     *
     * 👉 用到：InboundFlightZone
     *
     * Broker 收到 client 的 PUBLISH 时不能立即确认，要放在 InboundFlightZone 里。
     *
     * 等 client 发送 PUBREL 后，才移除，表示这条消息真正落地完成。
     *
     * Broker → Client （QoS2 同样适用）
     * Broker                           Client
     *   |---- PUBLISH(msgId=300) ------>|
     *   |   (保存到 OutboundFlightZone) |
     *   |                               |
     *   |<--- PUBREC(300) ---------------|
     *   |                               |
     *   |---- PUBREL(300) -------------->|
     *   |                               |
     *   |<--- PUBCOMP(300) --------------|
     *   |   (从 OutboundFlightZone移除) |
     *
     *
     * 👉 用到：OutboundFlightZone
     *
     * Broker 推 QoS2 消息时，先存起来。
     *
     * 等 client 发回 PUBCOMP，才彻底删除。
     *
     * ✅ 总结一句话：
     *
     * OutboundFlightZone：存储 Broker → Client 在飞行中的消息，等 client 确认。
     *
     * InboundFlightZone：存储 Client → Broker 在飞行中的消息，等 client 释放 (PUBREL)。
     */

    /**
     * 出站（发送出去的消息等待确认）
     */
    private OutboundFlightZone outboundFlightZone;

    /**
     * 入站（接收到的消息等待确认）
     */
    private InboundFlightZone inboundFlightZone;

    private AtomicInteger packetGenerator = new AtomicInteger(1);

    //private List<MqttSubscription> subscriptions = new ArrayList<MqttSubscription>();//订阅列表

    private List<OfflineMessage> offlineMessages = new ArrayList<OfflineMessage>(); // 离线消息（QoS > 0）

    private  MqttNettyChannel channel;

    private int alreadySendBusinessMsgId;

    class InboundFlightZone {
        private Map<Integer, StoredMessage> inboundFlightMessages = new ConcurrentHashMap<>();

        public StoredMessage relOk(int messageID) {
            return inboundFlightMessages.remove(messageID);
        }

        public void relWaiting(int messageID, StoredMessage msg) {
            inboundFlightMessages.put(messageID, msg);
        }

        public void clear(){
            inboundFlightMessages.clear();
        }

        public int size(){
            return inboundFlightMessages.size();
        }
    }


    class OutboundFlightZone {
        private Map<Integer, StoredMessage> outboundFlightMessages = new ConcurrentHashMap<>();

        public void ackWaiting(int messageID, StoredMessage msg) {

            outboundFlightMessages.put(messageID, msg);
        }

        public StoredMessage ackOk(int messageID) {
            return outboundFlightMessages.remove(messageID);
        }

        public void clear(){
            outboundFlightMessages.clear();
        }

        public int size(){
            return outboundFlightMessages.size();
        }
    }


    public static class QueueMsg{
        private int msgId;

        private StoredMessage msg;

        private volatile int pushCount = 0;

        private volatile long lastPushTime = System.currentTimeMillis();

        public QueueMsg(int msgId, StoredMessage msg){
            this.msg = msg;
            this.msgId = msgId;
        }

        public int getMsgId() {
            return msgId;
        }

        public StoredMessage getMsg() {
            return msg;
        }

        public int getPushCount() {
            return pushCount;
        }

        public void setPushCount(int pushCount) {
            this.pushCount = pushCount;
        }

        public void incrPushCount(int count) {
            this.pushCount += count;
        }

        public long getLastPushTime() {
            return lastPushTime;
        }

        public void setLastPushTime(long lastPushTime) {
            this.lastPushTime = lastPushTime;
        }
    }


    public ClientSession(String clientId, ISubscriptionsDirectory subscriptionsDirectory, MqttNettyChannel channel) {
        this.channel = channel;

        this.clientId = clientId;

        this.outboundFlightZone = new OutboundFlightZone();
        this.inboundFlightZone = new InboundFlightZone();

        this.subscriptionsDirectory = subscriptionsDirectory;
    }


    public boolean haveSend(int pushMessageId) {
/*		if( pushMessageId == 0 ){
			return false;
		}*/

        if (alreadySendBusinessMsgId > pushMessageId) {
            return true;
        }

        alreadySendBusinessMsgId = pushMessageId;

        return false;
    }


    public boolean isSameChannel(MqttNettyChannel currentChannel){
        return channel == currentChannel;
    }

    public boolean writeAndFlush(Object msg) throws Exception{
        return channel.writeAndFlush(msg);
    }

    public void fireEvent(Object event){
        channel.fireEvent(event);
    }

    private int nextPacketId() {
        return this.packetGenerator.getAndIncrement();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{clientId='" + clientId + "'}";
    }


    public void close(){
        //关闭通道
        if( null != channel ){
            channel.close();
            //设置netty channel为空，释放内存资源以便垃圾回收处理
            channel = null;
        }

        // 1移除订阅相关的
        Map<Topic, Subscription> subscriptions = getSubscriptions();
        for(Topic topic : subscriptions.keySet()){
            subscriptionsDirectory.removeSubscription(subscriptions.get(topic));
        }

        outboundFlightZone = null;
        inboundFlightZone = null;
    }

    public void disconnectChannel(boolean clearQueueAndInflight) {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (clearQueueAndInflight) {
            try {
                sendQueue.clear();
            } catch (Exception ignored) {
            }
            try {
                if (outboundFlightZone != null) {
                    outboundFlightZone.clear();
                }
            } catch (Exception ignored) {
            }
            try {
                if (inboundFlightZone != null) {
                    inboundFlightZone.clear();
                }
            } catch (Exception ignored) {
            }
        }
    }



    public long getConnectTime() {
        return connectTime;
    }

    public void setConnectTime(long connectTime) {
        this.connectTime = connectTime;
    }

    public void updateConnectedTime() {
        setConnectTime(System.currentTimeMillis());
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void touch(){
        this.lastPacketReciveTime = System.currentTimeMillis();
    }

    public boolean isIdle(){
        return System.currentTimeMillis() - lastPacketReciveTime > 2* MqttConstant.CHANNEL_TIMEOUT_SECONDS*1000;
    }

    public String remoteAddr(){
        return getChannel().remoteAddr();
    }

    public MqttNettyChannel getChannel() {
        return channel;
    }

    public void setChannel(MqttNettyChannel channel) {
        this.channel = channel;
    }

    public boolean subscribe(Topic topic, Subscription newSubscription) {

        subscriptions.put(topic, newSubscription);

        return true;
    }


    public void unsubscribe(Topic topicFilter) {
        subscriptions.remove(topicFilter);
    }

    public Map<Topic, Subscription> getSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    public Subscription getSubscription(Topic topic){
        return subscriptions.get(topic);
    }

    private void outBoundMsgAckWaiting(int messageId, StoredMessage msg) {
        synchronized (this) {
            if(MqttQoS.AT_MOST_ONCE != msg.getQos()){
                outboundFlightZone.ackWaiting(messageId, msg); // ⬅️ 加入出站飞行区
            }
        }
    }

    private StoredMessage outBoundMsgAckOk(int messageID) {
        synchronized (this) {
            return outboundFlightZone.ackOk(messageID); // ⬅️ 收到确认后移除
        }
    }


    public void inBoundMsgRelWaiting(int messageID, StoredMessage msg) {
        synchronized (this) {
            inboundFlightZone.relWaiting(messageID, msg); // ⬅️ 收到 PUBLISH，暂存
        }
    }

    public StoredMessage inBoundMsgRelOk(int messageID) {
        synchronized (this) {
            return inboundFlightZone.relOk(messageID); // ⬅️ 收到 PUBREL，确认投递
        }
    }

    public int inSendMsgSize(){
        synchronized (this) {
            return outboundFlightZone.size();
        }
    }

    public void addSendMsg(StoredMessage msg){

        QueueMsg queueMsg = new QueueMsg(nextPacketId(), msg);

        sendQueue.add(queueMsg);
    }

    public QueueMsg peekNextSendMsg(){
        synchronized (this) {
            QueueMsg msg = sendQueue.peek();
            if(null == msg){
                return null;
            }

            if(MqttQoS.AT_MOST_ONCE != msg.getMsg().getQos()){
                outBoundMsgAckWaiting(msg.getMsgId(), msg.getMsg());
            }

            return msg;
        }
    }

    public StoredMessage removeHaveSendMsg(int messageID){
        synchronized (this) {
            QueueMsg msg = sendQueue.peek();
            if (null != msg && messageID != msg.msgId) {
                //logger.info("不是同一个消息, ackMsgId={}, waitingMsgId={}", messageID, msg.getMsgId());
            } else {
                sendQueue.poll();
            }
            return outBoundMsgAckOk(messageID);
        }
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public WillMessage getWillMessage() {
        return willMessage;
    }

    public void setWillMessage(WillMessage willMessage) {
        this.willMessage = willMessage;
    }

    public long getLastPacketReciveTime() {
        return lastPacketReciveTime;
    }

    public void setLastPacketReciveTime(long lastPacketReciveTime) {
        this.lastPacketReciveTime = lastPacketReciveTime;
    }


}
