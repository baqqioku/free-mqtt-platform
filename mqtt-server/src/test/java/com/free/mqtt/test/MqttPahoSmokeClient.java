package com.free.mqtt.test;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MqttPahoSmokeClient {

    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println("Usage: <brokerUrl> <clientId> <userName> <password> <topic> <containsText> <timeoutSeconds>");
            System.exit(2);
        }

        String brokerUrl = args[0];
        String clientId = args[1];
        String userName = args[2];
        String password = args[3];
        String topic = args[4];
        String containsText = args[5];
        long timeoutSeconds = Long.parseLong(args[6]);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        MqttClient client = null;
        try {
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    if (payload.contains(containsText)) {
                        received.set(payload);
                        latch.countDown();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setUserName(userName);
            options.setPassword(password.toCharArray());

            client.connect(options);
            client.subscribe(topic, 1);

            boolean ok = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!ok) {
                System.err.println("TIMEOUT waiting message. topic=" + topic + " contains=" + containsText);
                System.exit(1);
            }
            System.out.println(received.get());
            System.exit(0);
        } catch (MqttException e) {
            System.err.println("MQTT error: " + e.getMessage());
            System.exit(3);
        } finally {
            if (client != null) {
                try {
                    if (client.isConnected()) {
                        client.disconnect();
                    }
                } catch (Exception ignored) {
                }
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}

