package com.free.mqtt.client;

public interface MqttMessageListener {
    void onMessage(String topic, byte[] payload, boolean retained, int qos);
}

