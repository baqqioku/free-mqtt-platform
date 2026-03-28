package com.free.mqtt.client;

import com.free.mqtt.client.http.RouteHttpClient;
import com.free.mqtt.client.model.BrokerInfo;
import com.free.mqtt.client.model.UserInfo;
import com.free.mqtt.client.paho.PahoFreeMqttClient;

public class FreeMqttAppClient implements AutoCloseable {

    private final RouteHttpClient routeHttpClient;
    private volatile UserInfo user;
    private volatile BrokerInfo broker;
    private volatile PahoFreeMqttClient mqttClient;

    public FreeMqttAppClient(RouteHttpClient routeHttpClient) {
        this.routeHttpClient = routeHttpClient;
    }

    public UserInfo register(String userName) throws Exception {
        user = routeHttpClient.register(userName);
        return user;
    }

    public BrokerInfo login(String userName, String token) throws Exception {
        broker = routeHttpClient.login(userName, token);
        return broker;
    }

    public void connectMqtt() throws Exception {
        if (broker == null || user == null) {
            throw new IllegalStateException("missing user/broker, call register/login first");
        }
        MqttClientConfig cfg = new MqttClientConfig();
        cfg.setBrokerUrl("tcp://" + broker.getIp() + ":" + broker.getTcpPort());
        cfg.setClientId(broker.getClientId());
        cfg.setUserName(user.getUserName());
        cfg.setPassword(user.getToken());
        mqttClient = new PahoFreeMqttClient(cfg);
        mqttClient.connect();
    }

    public void subscribe(String topicFilter, int qos, MqttMessageListener listener) throws Exception {
        if (mqttClient == null) {
            throw new IllegalStateException("mqtt not connected");
        }
        mqttClient.subscribe(topicFilter, qos, listener);
    }

    public void publish(String topic, byte[] payload, int qos, boolean retained) throws Exception {
        if (mqttClient == null) {
            throw new IllegalStateException("mqtt not connected");
        }
        mqttClient.publish(topic, payload, qos, retained);
    }

    public void disconnect() throws Exception {
        if (mqttClient != null) {
            mqttClient.close();
            mqttClient = null;
        }
    }

    @Override
    public void close() throws Exception {
        disconnect();
    }
}

