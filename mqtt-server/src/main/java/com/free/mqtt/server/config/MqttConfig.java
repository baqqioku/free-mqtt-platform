package com.free.mqtt.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {
    // 待发送消息堆积阈值
    private int waitPushPileUpSize;

    // 向app发送消息最大重试次数
    private int retrySendCount;
    // 向app发送消息未收到ack重试发送的时间间隔，毫秒
    private int sendWaitAckTime;
    // 向app发送消息之后，重试发送的定时时间，秒
    private int retrySendDelay;

    private long slowTime;// 200ms

    private long routeSlowTime;// 200ms

    private long sendSlowTime;// 200ms

    private long recvSendAckSlowTime; // 500ms

    public int getRetrySendCount() {
        return retrySendCount;
    }

    @Value("${mqtt.metric.config.retrySendCount:2}")
    public void setRetrySendCount(int retrySendCount) {
        this.retrySendCount = retrySendCount;
    }

    public int getSendWaitAckTime() {
        return sendWaitAckTime;
    }

    @Value("${mqtt.metric.config.sendWaitAckTime:1999}")
    public void setSendWaitAckTime(int sendWaitAckTime) {
        this.sendWaitAckTime = sendWaitAckTime;
    }

    public int getRetrySendDelay() {
        return retrySendDelay;
    }

    @Value("${mqtt.metric.config.retrySendDelay:2}")
    public void setRetrySendDelay(int retrySendDelay) {
        this.retrySendDelay = retrySendDelay;
    }

    public long getSlowTime() {
        return slowTime;
    }

    public int getWaitPushPileUpSize() {
        return waitPushPileUpSize;
    }

    @Value("${mqtt.metric.config.waitPushPileUpSize:1000}")
    public void setWaitPushPileUpSize(int waitPushPileUpSize) {
        this.waitPushPileUpSize = waitPushPileUpSize;
    }

    @Value("${mqtt.metric.config.slowTime:200}")
    public void setSlowTime(long slowTime) {
        this.slowTime = slowTime;
    }

    public long getRouteSlowTime() {
        return routeSlowTime;
    }

    @Value("${mqtt.metric.config.routeSlowTime:200}")
    public void setRouteSlowTime(long routeSlowTime) {
        this.routeSlowTime = routeSlowTime;
    }

    public long getSendSlowTime() {
        return sendSlowTime;
    }

    @Value("${mqtt.metric.config.sendSlowTime:200}")
    public void setSendSlowTime(long sendSlowTime) {
        this.sendSlowTime = sendSlowTime;
    }

    public long getRecvSendAckSlowTime() {
        return recvSendAckSlowTime;
    }

    @Value("${mqtt.metric.config.recvSendAckSlowTime:500}")
    public void setRecvSendAckSlowTime(long recvSendAckSlowTime) {
        this.recvSendAckSlowTime = recvSendAckSlowTime;
    }
}
