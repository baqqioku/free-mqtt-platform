package com.free.mqtt.client.app;

import com.free.mqtt.client.MqttClientConfig;
import com.free.mqtt.client.WillOptions;
import com.free.mqtt.client.http.RouteHttpClient;
import com.free.mqtt.client.model.BrokerInfo;
import com.free.mqtt.client.model.UserInfo;
import com.free.mqtt.client.paho.PahoFreeMqttClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttVersion;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreeMqttClientApp {

    public static void main(String[] args) throws Exception {
        Map<String, String> p = parseArgs(args);
        String mode = p.getOrDefault("mode", "daemon");

        if ("daemon".equalsIgnoreCase(mode)) {
            runDaemon(p);
            return;
        }
        if ("subWait".equalsIgnoreCase(mode)) {
            runSubWait(p);
            return;
        }
        if ("retainExpect".equalsIgnoreCase(mode)) {
            runRetainExpect(p);
            return;
        }
        if ("pub".equalsIgnoreCase(mode)) {
            runPublish(p);
            return;
        }
        if ("count".equalsIgnoreCase(mode)) {
            runCount(p);
            return;
        }
        if ("batchExpect".equalsIgnoreCase(mode)) {
            runBatchExpect(p);
            return;
        }
        if ("willPublisher".equalsIgnoreCase(mode)) {
            runWillPublisher(p);
            return;
        }
        if ("noAck".equalsIgnoreCase(mode)) {
            runNoAck(p);
            return;
        }

        System.err.println("Unknown mode: " + mode);
        System.exit(2);
    }

    private static void runDaemon(Map<String, String> p) throws Exception {
        try (PahoFreeMqttClient client = createMqttClient(p)) {
            String subs = p.get("sub");
            int subQos = parseInt(p.getOrDefault("subQos", "1"), 1);
            if (subs != null && !subs.trim().isEmpty()) {
                for (String s : subs.split(",")) {
                    String f = s.trim();
                    if (!f.isEmpty()) {
                        client.subscribe(f, subQos, (topic, payload, retained, qos) -> {
                            String text = new String(payload, StandardCharsets.UTF_8);
                            System.out.println("topic=" + topic + " qos=" + qos + " retained=" + retained + " payload=" + text);
                        });
                    }
                }
            }
            client.connect();
            String pubTopic = p.get("pubTopic");
            if (pubTopic != null && !pubTopic.trim().isEmpty()) {
                String payload = p.getOrDefault("pubPayload", "");
                int qos = parseInt(p.getOrDefault("pubQos", "1"), 1);
                boolean retained = Boolean.parseBoolean(p.getOrDefault("pubRetain", "false"));
                client.publish(pubTopic, payload.getBytes(StandardCharsets.UTF_8), qos, retained);
            }
            while (true) {
                TimeUnit.SECONDS.sleep(60);
            }
        }
    }

    private static void runSubWait(Map<String, String> p) throws Exception {
        String topic = required(p, "topic");
        String contains = required(p, "contains");
        int qos = parseInt(p.getOrDefault("qos", "1"), 1);
        long timeoutSeconds = parseLong(p.getOrDefault("timeoutSeconds", "30"), 30);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        try (PahoFreeMqttClient client = createMqttClient(p)) {
            client.subscribe(topic, qos, (t, payload, retained, q) -> {
                String text = new String(payload, StandardCharsets.UTF_8);
                if (text.contains(contains)) {
                    received.set(text);
                    latch.countDown();
                }
            });
            client.connect();
            boolean ok = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!ok) {
                System.err.println("TIMEOUT waiting message. topic=" + topic + " contains=" + contains);
                System.exit(1);
            }
            System.out.println(received.get());
            System.exit(0);
        }
    }

    private static void runRetainExpect(Map<String, String> p) throws Exception {
        String topic = required(p, "topic");
        String contains = required(p, "contains");
        boolean expectRetained = Boolean.parseBoolean(required(p, "expectRetained"));
        int qos = parseInt(p.getOrDefault("qos", "1"), 1);
        long timeoutSeconds = parseLong(p.getOrDefault("timeoutSeconds", "30"), 30);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<Boolean> retained = new AtomicReference<>(false);

        try (PahoFreeMqttClient client = createMqttClient(p)) {
            client.subscribe(topic, qos, (t, payload, isRetained, q) -> {
                String text = new String(payload, StandardCharsets.UTF_8);
                if (text.contains(contains)) {
                    received.set(text);
                    retained.set(isRetained);
                    latch.countDown();
                }
            });
            client.connect();
            boolean ok = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!ok) {
                System.err.println("TIMEOUT waiting message. topic=" + topic + " contains=" + contains);
                System.exit(1);
            }
            if (retained.get() != expectRetained) {
                System.err.println("RETAIN_MISMATCH expect=" + expectRetained + " got=" + retained.get());
                System.exit(4);
            }
            System.out.println(received.get());
            System.exit(0);
        }
    }

    private static void runPublish(Map<String, String> p) throws Exception {
        String topic = required(p, "topic");
        String payload = p.getOrDefault("payload", "");
        int qos = parseInt(p.getOrDefault("qos", "1"), 1);
        boolean retained = Boolean.parseBoolean(p.getOrDefault("retain", "false"));
        try (PahoFreeMqttClient client = connectMqtt(p)) {
            client.publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, retained);
            System.exit(0);
        }
    }

    private static void runCount(Map<String, String> p) throws Exception {
        String topic = required(p, "topic");
        String contains = required(p, "contains");
        int qos = parseInt(p.getOrDefault("qos", "1"), 1);
        long durationSeconds = parseLong(p.getOrDefault("durationSeconds", "5"), 5);
        int expected = parseInt(required(p, "expectedCount"), 0);

        AtomicInteger count = new AtomicInteger(0);
        try (PahoFreeMqttClient client = createMqttClient(p)) {
            client.subscribe(topic, qos, (t, payload, retained, q) -> {
                String text = new String(payload, StandardCharsets.UTF_8);
                if (text.contains(contains)) {
                    count.incrementAndGet();
                }
            });
            client.connect();
            TimeUnit.SECONDS.sleep(durationSeconds);
        }
        int got = count.get();
        if (got != expected) {
            System.err.println("COUNT_MISMATCH expected=" + expected + " got=" + got);
            System.exit(1);
        }
        System.out.println("OK count=" + got);
        System.exit(0);
    }

    private static void runBatchExpect(Map<String, String> p) throws Exception {
        String topic = required(p, "topic");
        String prefix = required(p, "markerPrefix");
        int qos = parseInt(p.getOrDefault("qos", "1"), 1);
        int expectedCount = parseInt(required(p, "expectedCount"), 0);
        int minIndex = parseInt(required(p, "minIndex"), 0);
        int maxIndex = parseInt(required(p, "maxIndex"), 0);
        long timeoutSeconds = parseLong(p.getOrDefault("timeoutSeconds", "30"), 30);

        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "(\\d+)");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (PahoFreeMqttClient client = createMqttClient(p)) {
            client.subscribe(topic, qos, (t, payload, retained, q) -> {
                String text = new String(payload, StandardCharsets.UTF_8);
                Matcher m = pattern.matcher(text);
                if (!m.find()) {
                    return;
                }
                int idx;
                try {
                    idx = Integer.parseInt(m.group(1));
                } catch (Exception e) {
                    return;
                }
                if (idx < minIndex || idx > maxIndex) {
                    System.err.println("OUT_OF_RANGE idx=" + idx + " payload=" + text);
                    System.exit(4);
                }
                seen.add(idx);
                if (seen.size() >= expectedCount) {
                    latch.countDown();
                }
            });
            client.connect();
            boolean ok = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!ok) {
                System.err.println("TIMEOUT expected=" + expectedCount + " got=" + seen.size() + " seen=" + seen);
                System.exit(1);
            }
        }
        System.out.println("OK seen=" + seen);
        System.exit(0);
    }

    private static void runWillPublisher(Map<String, String> p) throws Exception {
        long sleepSeconds = parseLong(p.getOrDefault("sleepSeconds", "60"), 60);
        boolean forceExit = Boolean.parseBoolean(p.getOrDefault("forceExit", "false"));
        PahoFreeMqttClient client = connectMqtt(p);
        if (forceExit) {
            TimeUnit.SECONDS.sleep(sleepSeconds);
            System.exit(0);
            return;
        }
        try {
            TimeUnit.SECONDS.sleep(sleepSeconds);
            System.exit(0);
        } finally {
            client.close();
        }
    }

    private static void runNoAck(Map<String, String> p) throws Exception {
        String brokerUrl = required(p, "brokerUrl");
        String clientId = required(p, "clientId");
        String userName = required(p, "userName");
        String password = required(p, "password");
        String topic = required(p, "topic");
        String contains = required(p, "contains");
        int timeoutSeconds = parseInt(p.getOrDefault("timeoutSeconds", "10"), 10);

        HostPort hp = parseHostPort(brokerUrl);
        CountDownLatch received = new CountDownLatch(1);
        StringBuilder payloadOut = new StringBuilder();

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Channel channel = null;
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast("decoder", new MqttDecoder(1024 * 1024));
                            ch.pipeline().addLast("encoder", MqttEncoder.INSTANCE);
                            ch.pipeline().addLast("handler", new SimpleChannelInboundHandler<MqttMessage>() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    MqttConnectMessage connect = MqttMessageBuilders.connect()
                                            .protocolVersion(MqttVersion.MQTT_3_1_1)
                                            .clientId(clientId)
                                            .username(userName)
                                            .password(password.getBytes(StandardCharsets.UTF_8))
                                            .keepAlive(10)
                                            .cleanSession(true)
                                            .build();
                                    ctx.writeAndFlush(connect);
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
                                    if (msg.fixedHeader() == null) {
                                        return;
                                    }
                                    MqttMessageType type = msg.fixedHeader().messageType();
                                    if (type == MqttMessageType.CONNACK) {
                                        MqttConnAckMessage connAck = (MqttConnAckMessage) msg;
                                        if (connAck.variableHeader().connectReturnCode() == null ||
                                                connAck.variableHeader().connectReturnCode() != MqttConnectReturnCode.CONNECTION_ACCEPTED) {
                                            ctx.close();
                                            return;
                                        }
                                        int mid = packetId.getAndIncrement();
                                        MqttSubscribeMessage sub = MqttMessageBuilders.subscribe()
                                                .messageId(mid)
                                                .addSubscription(MqttQoS.AT_LEAST_ONCE, topic)
                                                .build();
                                        ctx.writeAndFlush(sub);
                                        return;
                                    }
                                    if (type == MqttMessageType.SUBACK) {
                                        MqttSubAckMessage subAck = (MqttSubAckMessage) msg;
                                        if (subAck.payload() == null || subAck.payload().grantedQoSLevels() == null || subAck.payload().grantedQoSLevels().isEmpty()) {
                                            ctx.close();
                                        }
                                        return;
                                    }
                                    if (type == MqttMessageType.PUBLISH) {
                                        MqttPublishMessage pub = (MqttPublishMessage) msg;
                                        ByteBuf bb = pub.payload();
                                        byte[] bytes = new byte[bb.readableBytes()];
                                        bb.getBytes(bb.readerIndex(), bytes);
                                        String payload = new String(bytes, StandardCharsets.UTF_8);
                                        payloadOut.append(payload);
                                        if (payload.contains(contains)) {
                                            received.countDown();
                                        }
                                        ctx.close();
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    ctx.close();
                                }
                            });
                        }
                    });

            channel = bootstrap.connect(hp.host, hp.port).sync().channel();
            boolean ok = received.await(timeoutSeconds, TimeUnit.SECONDS);
            if (ok) {
                System.out.println(payloadOut);
                System.exit(0);
            } else {
                System.out.println("TIMEOUT");
                if (payloadOut.length() > 0) {
                    System.out.println(payloadOut);
                }
                System.exit(2);
            }
        } finally {
            try {
                if (channel != null) {
                    channel.close().sync();
                }
            } catch (Exception ignored) {
            }
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    private static final AtomicInteger packetId = new AtomicInteger(1);

    private static PahoFreeMqttClient createMqttClient(Map<String, String> p) throws Exception {
        ConnParams conn = resolveConnParams(p);
        MqttClientConfig cfg = new MqttClientConfig();
        boolean ssl = Boolean.parseBoolean(p.getOrDefault("ssl", "false"));
        String brokerUrl = conn.brokerUrl;
        if (ssl && brokerUrl != null && brokerUrl.startsWith("tcp://")) {
            brokerUrl = "ssl://" + brokerUrl.substring("tcp://".length());
        }
        cfg.setBrokerUrl(brokerUrl);
        cfg.setClientId(conn.clientId);
        cfg.setUserName(conn.userName);
        cfg.setPassword(conn.password);
        cfg.setCleanSession(Boolean.parseBoolean(p.getOrDefault("cleanSession", "true")));
        cfg.setAutomaticReconnect(Boolean.parseBoolean(p.getOrDefault("automaticReconnect", "true")));
        cfg.setKeepAliveSeconds(parseInt(p.getOrDefault("keepAliveSeconds", "10"), 10));
        cfg.setConnectionTimeoutSeconds(parseInt(p.getOrDefault("connectionTimeoutSeconds", "10"), 10));
        cfg.setSslEnabled(ssl);
        cfg.setSslInsecure(Boolean.parseBoolean(p.getOrDefault("sslInsecure", "false")));
        cfg.setTrustStorePath(p.get("trustStorePath"));
        cfg.setTrustStorePassword(p.get("trustStorePassword"));
        cfg.setTrustStoreType(p.getOrDefault("trustStoreType", "JKS"));
        cfg.setKeyStorePath(p.get("keyStorePath"));
        cfg.setKeyStorePassword(p.get("keyStorePassword"));
        cfg.setKeyStoreType(p.getOrDefault("keyStoreType", "JKS"));

        String willTopic = p.get("willTopic");
        if (willTopic != null && !willTopic.trim().isEmpty()) {
            String willPayload = p.getOrDefault("willPayload", "");
            int willQos = parseInt(p.getOrDefault("willQos", "1"), 1);
            boolean willRetain = Boolean.parseBoolean(p.getOrDefault("willRetain", "false"));
            cfg.setWill(WillOptions.of(willTopic, willPayload, willQos, willRetain));
        }

        return new PahoFreeMqttClient(cfg);
    }

    private static PahoFreeMqttClient connectMqtt(Map<String, String> p) throws Exception {
        PahoFreeMqttClient client = createMqttClient(p);
        client.connect();
        return client;
    }

    private static ConnParams resolveConnParams(Map<String, String> p) throws Exception {
        String brokerUrl = p.get("brokerUrl");
        String clientId = p.get("clientId");
        String userName = p.get("userName");
        String password = p.get("password");

        if (brokerUrl != null && clientId != null && userName != null && password != null) {
            return new ConnParams(brokerUrl, clientId, userName, password);
        }

        String routeUrl = p.get("routeUrl");
        if (routeUrl == null || routeUrl.trim().isEmpty()) {
            System.err.println("Missing --brokerUrl/--clientId/--userName/--password or --routeUrl");
            System.exit(2);
        }
        String routeUserName = required(p, "userName");
        String token = p.get("token");
        RouteHttpClient route = new RouteHttpClient(routeUrl);
        UserInfo user;
        if (token == null || token.trim().isEmpty()) {
            user = route.register(routeUserName);
            token = user.getToken();
        } else {
            user = new UserInfo();
            user.setUserName(routeUserName);
            user.setToken(token);
        }
        BrokerInfo broker = route.login(routeUserName, token);
        String url = "tcp://" + broker.getIp() + ":" + broker.getTcpPort();
        return new ConnParams(url, broker.getClientId(), routeUserName, token);
    }

    private static class ConnParams {
        final String brokerUrl;
        final String clientId;
        final String userName;
        final String password;

        ConnParams(String brokerUrl, String clientId, String userName, String password) {
            this.brokerUrl = brokerUrl;
            this.clientId = clientId;
            this.userName = userName;
            this.password = password;
        }
    }

    private static class HostPort {
        final String host;
        final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static HostPort parseHostPort(String brokerUrl) throws Exception {
        URI uri = new URI(brokerUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        if (host != null && port > 0) {
            return new HostPort(host, port);
        }
        String raw = brokerUrl;
        if (raw.startsWith("tcp://")) {
            raw = raw.substring("tcp://".length());
        }
        String[] hp = raw.split(":");
        return new HostPort(hp[0], Integer.parseInt(hp[1]));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                continue;
            }
            String key = a.substring(2);
            String val = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                val = args[i + 1];
                i++;
            }
            m.put(key, val);
        }
        return m;
    }

    private static String required(Map<String, String> p, String k) {
        String v = p.get(k);
        if (v == null || v.trim().isEmpty()) {
            System.err.println("Missing --" + k);
            System.exit(2);
        }
        return v;
    }

    private static int parseInt(String s, int dflt) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return dflt;
        }
    }

    private static long parseLong(String s, long dflt) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return dflt;
        }
    }
}
