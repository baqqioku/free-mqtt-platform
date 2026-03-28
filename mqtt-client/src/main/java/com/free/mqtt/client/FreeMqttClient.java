package com.free.mqtt.client;

public interface FreeMqttClient extends AutoCloseable {

    void connect() throws Exception;

    void disconnect() throws Exception;

    boolean isConnected();

    void subscribe(String topicFilter, int qos, MqttMessageListener listener) throws Exception;

    void publish(String topic, byte[] payload, int qos, boolean retained) throws Exception;

    @Override
    void close() throws Exception;
}

