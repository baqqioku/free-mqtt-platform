package com.free.mqtt.server;

import com.free.mqtt.server.config.MqttServerConfig;
import com.free.mqtt.server.handler.MqttServerHandler;
import com.free.mqtt.server.interceptor.MqttInterceptor;
import com.free.mqtt.server.listener.MqttServerListener;
import com.free.mqtt.server.netty.MqttChannelInitializer;
import com.free.mqtt.server.qos.ProtocolProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;

/**
 * MQTT服务器主类
 */
@Component
public class MqttServer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttServer.class);
    
    @Autowired
    private MqttServerConfig config;
    
    @Autowired
    private MqttServerHandler mqttServerHandler;
    
    @Autowired
    private MqttInterceptor mqttInterceptor;
    
    @Autowired
    private MqttServerListener mqttServerListener;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    private static MqttServer instance;
    
    @PostConstruct
    public void init() {
        instance = this;
    }
    
    public static MqttServer getInstance() {
        return instance;
    }
    
    @Override
    public void run(String... args) throws Exception {
        start();
    }
    
    public void start() {
        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            
            ProtocolProcessor protocolProcessor = new ProtocolProcessor(mqttInterceptor, mqttServerListener);
            mqttServerHandler.setProtocolProcessor(protocolProcessor);
            
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new MqttChannelInitializer(mqttServerHandler));
            
            int port = config.getPort();
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            
            logger.info("MQTT Server started on port {}", port);
            
        } catch (Exception e) {
            logger.error("Failed to start MQTT Server", e);
            throw new RuntimeException(e);
        }
    }
    
    @PreDestroy
    public void stop() {
        logger.info("Stopping MQTT Server...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("MQTT Server stopped");
    }
}
