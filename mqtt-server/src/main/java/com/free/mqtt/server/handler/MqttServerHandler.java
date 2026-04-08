package com.free.mqtt.server.handler;

import com.free.mqtt.server.interceptor.MqttInterceptor;
import com.free.mqtt.server.qos.ProtocolProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MqttServerHandler extends SimpleChannelInboundHandler<MqttMessage> {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttServerHandler.class);
    
    @Autowired
    private MqttInterceptor mqttInterceptor;
    
    private ProtocolProcessor protocolProcessor;
    
    public void setProtocolProcessor(ProtocolProcessor protocolProcessor) {
        this.protocolProcessor = protocolProcessor;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
        logger.debug("Received MQTT message: {}", msg);
        
        if (protocolProcessor != null) {
            // Process message based on type
            switch (msg.fixedHeader().messageType()) {
                case CONNECT:
                    handleConnect(ctx, msg);
                    break;
                case PUBLISH:
                    handlePublish(ctx, msg);
                    break;
                case SUBSCRIBE:
                    handleSubscribe(ctx, msg);
                    break;
                case UNSUBSCRIBE:
                    handleUnsubscribe(ctx, msg);
                    break;
                case PINGREQ:
                    handlePingReq(ctx);
                    break;
                case DISCONNECT:
                    handleDisconnect(ctx, msg);
                    break;
                default:
                    logger.debug("Unhandled message type: {}", msg.fixedHeader().messageType());
            }
        }
    }
    
    private void handleConnect(ChannelHandlerContext ctx, MqttMessage msg) {
        logger.info("Client connected");
        if (mqttInterceptor != null) {
            mqttInterceptor.onConnect(ctx.channel().id().asLongText());
        }
    }
    
    private void handlePublish(ChannelHandlerContext ctx, MqttMessage msg) {
        logger.debug("Handling PUBLISH message");
    }
    
    private void handleSubscribe(ChannelHandlerContext ctx, MqttMessage msg) {
        logger.debug("Handling SUBSCRIBE message");
    }
    
    private void handleUnsubscribe(ChannelHandlerContext ctx, MqttMessage msg) {
        logger.debug("Handling UNSUBSCRIBE message");
    }
    
    private void handlePingReq(ChannelHandlerContext ctx) {
        logger.debug("Handling PINGREQ");
    }
    
    private void handleDisconnect(ChannelHandlerContext ctx, MqttMessage msg) {
        logger.info("Client disconnected");
        if (mqttInterceptor != null) {
            mqttInterceptor.onDisconnect(ctx.channel().id().asLongText());
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Channel active: {}", ctx.channel().id());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Channel inactive: {}", ctx.channel().id());
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception caught in channel", cause);
        super.exceptionCaught(ctx, cause);
    }
}
