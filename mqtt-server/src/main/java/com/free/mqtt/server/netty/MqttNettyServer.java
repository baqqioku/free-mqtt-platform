package com.free.mqtt.server.netty;

import com.free.common.constant.MqttConstant;
import com.free.mqtt.server.config.MqttServerConfig;
import com.free.mqtt.server.netty.handler.MqttMsgHandler;
import com.free.mqtt.server.netty.handler.MqttProcessHandler;
import com.free.mqtt.server.qos.ProtocolProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;


public class MqttNettyServer extends AbstractMqttServer {

    private static final Logger logger = LoggerFactory.getLogger(MqttNettyServer.class);

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;


    private int nettySoBacklog = 512;
    private boolean nettySoReuseaddr = true;
    private boolean nettyTcpNodelay = true;
    private boolean nettySoKeepalive = true;
    private boolean nettyUserEpoll = false;

    private RecvByteBufAllocator recvByteBufAllocator;
    private ByteBufAllocator byteBufAllocator;

    private Class<? extends ServerSocketChannel> nettyChannelClass;

    public MqttNettyServer(MqttServerConfig mqttServerConfig, MqttProcessHandler mqttProcessHandler) throws Exception {
        super(mqttServerConfig,mqttProcessHandler);
        init();
    }


    @Override
    public void init() throws Exception {
        byteBufAllocator = new UnpooledByteBufAllocator(false);
        recvByteBufAllocator = new AdaptiveRecvByteBufAllocator();

        if(nettyUserEpoll){
            bossGroup = new EpollEventLoopGroup();
            workerGroup = new EpollEventLoopGroup();
            nettyChannelClass = EpollServerSocketChannel.class;
        }else {
            bossGroup = new NioEventLoopGroup();
            workerGroup  = new NioEventLoopGroup();
            nettyChannelClass = NioServerSocketChannel.class;
        }

        //初始化mqtt集群
        clusterPlainTCPTransport();

        //ssl
        //initializeSSLTCPTransport();

        //初始化tcp serversocket
        initializePlainTCPTransport();

        //初始化web serversocket
        initializeWebSocketTransport();

    }

    private void clusterPlainTCPTransport() throws Exception {
        ChannelInitializer<SocketChannel> channelChannelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.config().setAllocator(byteBufAllocator);
                ch.config().setRecvByteBufAllocator(recvByteBufAllocator);
                ChannelPipeline pipeline= ch.pipeline();
                pipeline.addFirst("idleStaleHandler",new IdleStateHandler(MqttConstant.CHANNEL_TIMEOUT_SECONDS,0,0));
                pipeline.addLast("decoder",new MqttDecoder(MqttConstant.AGGREGATOR_MAX_SIZE));
                pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                pipeline.addLast("handler",new MqttMsgHandler(getMqttProcessHandler()));
            }
        };

        initFactory(MqttConstant.DEFAULT_HOST,serverConfig.getTcpPort(),"TCP MQTT", channelChannelInitializer);
    }

    /*private void initializeSSLTCPTransport() throws Exception{
        ChannelInitializer<SocketChannel> channelInitializer = new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.config().setAllocator(byteBufAllocator);
                ch.config().setRecvByteBufAllocator(recvByteBufAllocator);

                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addFirst("idleStateHandler", new IdleStateHandler(MqttConstant.CHANNEL_TIMEOUT_SECONDS, 0, 0));

                pipeline.addLast("ssl", createSslHandler(sslContext, false));

                pipeline.addLast("decoder", new MqttDecoder(MqttConstant.AGGREGATOR_MAX_SIZE));
                pipeline.addLast("encoder", MqttEncoder.INSTANCE);

                pipeline.addLast("handler",new MqttMsgHandler(getMqttProcessHandler()));
            }

        };


        initFactory(MqttConstant.DEFAULT_HOST, serverConfig.getTcpSslTcpPort(), "SSL MQTT", channelInitializer);
    }*/

    private void initFactory(String host, int port, String protocol, ChannelInitializer channelInitializer) throws Exception {
        logger.info("Initializing server. Protocol={}", protocol);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup);
            b.channel(nettyChannelClass);

            // 作用于 ServerChannel（监听 socket）
            b.option(ChannelOption.SO_BACKLOG, nettySoBacklog);
            b.option(ChannelOption.SO_REUSEADDR, nettySoReuseaddr);

            // 作用于子连接（真正的数据传输）
            b.childOption(ChannelOption.TCP_NODELAY, nettyTcpNodelay);
            b.childOption(ChannelOption.SO_KEEPALIVE, nettySoKeepalive);
            b.childOption(ChannelOption.ALLOCATOR, byteBufAllocator);
            b.childOption(ChannelOption.RCVBUF_ALLOCATOR, recvByteBufAllocator);
            b.childHandler(channelInitializer);

            logger.info("Binding server. host={}, port={}", host, port);

            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(host, port);

            logger.info("Server has been bound. host={}, port={}", host, port);

            f.sync().channel();
        } catch (Exception ex) {
            logger.error("初始化mqtt服务异常  port:{}", port, ex);

            throw ex;
        }
    }

    private void initializePlainTCPTransport(){
        // TODO: 2025/8/22
    }

    private void initializeWebSocketTransport(){
        // TODO: 2025/8/22
    }

    private SslHandler createSslHandler(SslContext sslContext, boolean needsClientAuth) {
        SSLEngine sslEngine = sslContext.newEngine(ByteBufAllocator.DEFAULT);
        sslEngine.setUseClientMode(false);
        if (needsClientAuth) {
            sslEngine.setNeedClientAuth(true);
        }
        return new SslHandler(sslEngine);
    }

    @Override
    public void stop(){
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }
}
