package com.free.mqtt.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MqttNoAckSmokeClient {

    private static final AtomicInteger packetId = new AtomicInteger(1);

    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println("Usage: MqttNoAckSmokeClient <tcp://host:port> <clientId> <userName> <token> <topic> <marker> <timeoutSeconds>");
            System.exit(3);
        }

        String brokerUrl = args[0];
        String clientId = args[1];
        String userName = args[2];
        String token = args[3];
        String topic = args[4];
        String marker = args[5];
        int timeoutSeconds = Integer.parseInt(args[6]);

        URI uri = new URI(brokerUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null || port <= 0) {
            String raw = brokerUrl;
            if (raw.startsWith("tcp://")) {
                raw = raw.substring("tcp://".length());
            }
            String[] hp = raw.split(":");
            host = hp[0];
            port = Integer.parseInt(hp[1]);
        }

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
                                            .password(token.getBytes(StandardCharsets.UTF_8))
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
                                                connAck.variableHeader().connectReturnCode() != io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED) {
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
                                        if (payload.contains(marker)) {
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

            channel = bootstrap.connect(host, port).sync().channel();

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
}
