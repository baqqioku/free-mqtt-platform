package com.free.mqtt.test;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MqttPahoBatchExpectClient {

    public static void main(String[] args) throws Exception {
        if (args.length < 10) {
            System.err.println("Usage: <brokerUrl> <clientId> <userName> <password> <topic> <markerPrefix> <expectedCount> <minIndex> <maxIndex> <timeoutSeconds>");
            System.exit(2);
        }

        String brokerUrl = args[0];
        String clientId = args[1];
        String userName = args[2];
        String password = args[3];
        String topic = args[4];
        String markerPrefix = args[5];
        int expectedCount = Integer.parseInt(args[6]);
        int minIndex = Integer.parseInt(args[7]);
        int maxIndex = Integer.parseInt(args[8]);
        long timeoutSeconds = Long.parseLong(args[9]);

        Pattern p = Pattern.compile(Pattern.quote(markerPrefix) + "(\\d+)");
        Set<Integer> seen = new HashSet<>();
        int[] totalMatched = new int[] {0};
        CountDownLatch latch = new CountDownLatch(1);

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
                    Matcher m = p.matcher(payload);
                    if (!m.find()) {
                        return;
                    }
                    totalMatched[0]++;
                    int idx;
                    try {
                        idx = Integer.parseInt(m.group(1));
                    } catch (Exception e) {
                        return;
                    }
                    if (idx < minIndex || idx > maxIndex) {
                        System.err.println("OUT_OF_RANGE idx=" + idx + " payload=" + payload);
                        System.exit(4);
                    }
                    seen.add(idx);
                    if (seen.size() >= expectedCount) {
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
                System.err.println("TIMEOUT expected=" + expectedCount + " got=" + seen.size() + " seen=" + seen);
                System.exit(1);
            }

            System.out.println("OK seen=" + seen);
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
