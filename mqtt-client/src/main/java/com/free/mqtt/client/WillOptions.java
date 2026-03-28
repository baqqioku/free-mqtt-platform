package com.free.mqtt.client;

import java.nio.charset.StandardCharsets;

public class WillOptions {

    private final String topic;
    private final byte[] payload;
    private final int qos;
    private final boolean retained;

    public WillOptions(String topic, byte[] payload, int qos, boolean retained) {
        this.topic = topic;
        this.payload = payload;
        this.qos = qos;
        this.retained = retained;
    }

    public static WillOptions of(String topic, String payload, int qos, boolean retained) {
        return new WillOptions(topic, payload == null ? null : payload.getBytes(StandardCharsets.UTF_8), qos, retained);
    }

    public String getTopic() {
        return topic;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int getQos() {
        return qos;
    }

    public boolean isRetained() {
        return retained;
    }
}

