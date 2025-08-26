package com.free.mqtt.server.listener;

public interface MqttMsgListener {


    boolean mqttChannelAuth(String clientId, String userName, String password);

    String autoSub(String clientId);

}
