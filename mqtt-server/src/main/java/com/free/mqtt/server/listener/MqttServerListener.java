package com.free.mqtt.server.listener;

import com.free.mqtt.server.qos.StoredMessage;
import com.free.mqtt.server.subscriptions.Topic;

/**
 * MQTT服务器监听器
 */
public class MqttServerListener {
    
    public void onPublish(String clientId, Topic topic, StoredMessage message) {
    }
    
    public void onConnect(String clientId) {
    }
    
    public void onDisconnect(String clientId) {
    }
    
    public void onSubscribe(String clientId, Topic topic) {
    }
    
    public void onUnsubscribe(String clientId, Topic topic) {
    }
}
