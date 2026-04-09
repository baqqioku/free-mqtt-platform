package com.free.mqtt.server.handler;

import com.free.mqtt.server.interceptor.MqttInterceptor;
import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.qos.ProtocolProcessor;
import com.free.mqtt.server.utils.MqttNettyUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


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
            MqttNettyChannel channel = MqttNettyUtils.channel(ctx.channel());
            protocolProcessor.processMsg(channel, msg);
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
        if (protocolProcessor != null) {
            MqttNettyChannel channel = MqttNettyUtils.channel(ctx.channel());
            protocolProcessor.processConnectionLost(channel);
        }
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception caught in channel", cause);
        super.exceptionCaught(ctx, cause);
    }
}
