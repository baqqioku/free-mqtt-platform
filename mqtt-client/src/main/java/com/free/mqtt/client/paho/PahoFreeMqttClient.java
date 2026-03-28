package com.free.mqtt.client.paho;

import com.free.mqtt.client.FreeMqttClient;
import com.free.mqtt.client.MqttClientConfig;
import com.free.mqtt.client.MqttMessageListener;
import com.free.mqtt.client.WillOptions;
import com.free.mqtt.client.util.SslUtil;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSocketFactory;

public class PahoFreeMqttClient implements FreeMqttClient {

    private final MqttClientConfig config;
    private final Map<String, MqttMessageListener> listenerByTopicFilter = new ConcurrentHashMap<>();
    private final Map<String, Integer> qosByTopicFilter = new ConcurrentHashMap<>();

    private volatile MqttClient client;

    public PahoFreeMqttClient(MqttClientConfig config) {
        this.config = config;
    }

    @Override
    public void connect() throws Exception {
        if (client != null && client.isConnected()) {
            return;
        }
        client = new MqttClient(config.getBrokerUrl(), config.getClientId(), new MemoryPersistence());
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                for (Map.Entry<String, Integer> e : qosByTopicFilter.entrySet()) {
                    try {
                        client.subscribe(e.getKey(), e.getValue());
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                for (Map.Entry<String, MqttMessageListener> e : listenerByTopicFilter.entrySet()) {
                    if (topicMatch(e.getKey(), topic)) {
                        e.getValue().onMessage(topic, message.getPayload(), message.isRetained(), message.getQos());
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        client.connect(buildConnectOptions());
    }

    @Override
    public void disconnect() throws Exception {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
            client = null;
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void subscribe(String topicFilter, int qos, MqttMessageListener listener) throws Exception {
        listenerByTopicFilter.put(topicFilter, listener);
        qosByTopicFilter.put(topicFilter, qos);
        if (client != null && client.isConnected()) {
            client.subscribe(topicFilter, qos);
        }
    }

    @Override
    public void publish(String topic, byte[] payload, int qos, boolean retained) throws Exception {
        MqttMessage msg = new MqttMessage(payload);
        msg.setQos(qos);
        msg.setRetained(retained);
        client.publish(topic, msg);
    }

    @Override
    public void close() throws Exception {
        disconnect();
    }

    private MqttConnectOptions buildConnectOptions() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(config.isCleanSession());
        options.setAutomaticReconnect(config.isAutomaticReconnect());
        options.setKeepAliveInterval(config.getKeepAliveSeconds());
        options.setConnectionTimeout(config.getConnectionTimeoutSeconds());
        if (config.getUserName() != null) {
            options.setUserName(config.getUserName());
        }
        if (config.getPassword() != null) {
            options.setPassword(config.getPassword().toCharArray());
        }
        WillOptions will = config.getWill();
        if (will != null) {
            options.setWill(will.getTopic(), will.getPayload(), will.getQos(), will.isRetained());
        }
        if (config.isSslEnabled() || isSslUrl(config.getBrokerUrl())) {
            try {
                SSLSocketFactory factory = SslUtil.createSocketFactory(config);
                if (factory != null) {
                    options.setSocketFactory(factory);
                }
            } catch (Exception e) {
                throw new MqttException(e);
            }
        }
        return options;
    }

    private boolean isSslUrl(String url) {
        return url != null && url.startsWith("ssl://");
    }

    private boolean topicMatch(String filter, String topic) {
        if (filter == null || topic == null) {
            return false;
        }
        if (filter.equals(topic)) {
            return true;
        }
        String[] fs = filter.split("/");
        String[] ts = topic.split("/");
        int i = 0;
        for (; i < fs.length; i++) {
            String f = fs[i];
            if (f.equals("#")) {
                return true;
            }
            if (i >= ts.length) {
                return false;
            }
            if (f.equals("+")) {
                continue;
            }
            if (!f.equals(ts[i])) {
                return false;
            }
        }
        return i == ts.length;
    }
}
