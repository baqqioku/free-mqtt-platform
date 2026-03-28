package com.free.mqtt.test;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MqttPahoCountClient {

    public static void main(String[] args) throws Exception {
        if (args.length < 8) {
            System.err.println("Usage: <brokerUrl> <clientId> <userName> <password> <topic> <containsText> <durationSeconds> <expectedCount>");
            System.exit(2);
        }

        String brokerUrl = args[0];
        String clientId = args[1];
        String userName = args[2];
        String password = args[3];
        String topic = args[4];
        String containsText = args[5];
        long durationSeconds = Long.parseLong(args[6]);
        int expectedCount = Integer.parseInt(args[7]);

        AtomicInteger count = new AtomicInteger(0);

        MqttClient client = null;
        try {
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String t, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    if (payload.contains(containsText)) {
                        count.incrementAndGet();
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

            TimeUnit.SECONDS.sleep(durationSeconds);

            int got = count.get();
            if (got != expectedCount) {
                System.err.println("COUNT_MISMATCH expected=" + expectedCount + " got=" + got);
                System.exit(1);
            }
            System.out.println("OK count=" + got);
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

