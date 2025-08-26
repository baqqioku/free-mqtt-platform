package com.free.mqtt.server.utils;

import com.free.mqtt.server.config.MqttConfig;


public class PerfUtil {
    private static MqttConfig mqttConfig;

    public static void init(MqttConfig mqttConfig) {
        PerfUtil.mqttConfig = mqttConfig;
    }

    public static int getWaitPushPileUpSize() {
        return mqttConfig.getWaitPushPileUpSize();
    }

    public static int getRetrySendCount() {
        return mqttConfig.getRetrySendCount();
    }

    public static int getSendWaitAckTime() {
        int time = mqttConfig.getSendWaitAckTime();
        if (time <= 0) {
            return 1999;
        }
        return time;
    }

    public static int getRetrySendDelay() {
        int delay = mqttConfig.getRetrySendDelay();
        if (delay <= 0) {
            return 2;
        }
        return delay;
    }

    public static long getSlowTime() {
        return mqttConfig.getSlowTime();
    }

    public static boolean isRouteSlow(long time) {
        return System.currentTimeMillis() - time >= mqttConfig.getRouteSlowTime();
    }

    public static boolean isSendSlow(long time) {
        return System.currentTimeMillis() - time >= mqttConfig.getSendSlowTime();
    }

    public static boolean isRecvSendAckSlow(long time) {
        return System.currentTimeMillis() - time >= mqttConfig.getRecvSendAckSlowTime();
    }
}
