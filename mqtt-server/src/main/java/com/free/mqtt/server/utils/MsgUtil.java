package com.free.mqtt.server.utils;

public class MsgUtil {
    public static String toMsg(byte[] payload) {
        if (null == payload) {
            return "";
        }

        return new String(payload);
    }
}
