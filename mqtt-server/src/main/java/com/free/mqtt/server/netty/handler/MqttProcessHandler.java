package com.free.mqtt.server.netty.handler;

import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.qos.ProtocolProcessor;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttProcessHandler {

    private static final Logger logger = LoggerFactory.getLogger(MqttProcessHandler.class);

    private ProtocolProcessor protocolProcessor;

    public MqttProcessHandler(ProtocolProcessor protocolProcessor) {
        this.protocolProcessor = protocolProcessor;
    }

    //核心入口
    public void processMsg(MqttNettyChannel channel, MqttMessage msg) throws Exception {
        protocolProcessor.processMsg(channel, msg);
    }

    public void pushMsg(MqttNettyChannel channel) throws Exception{
        //避免通道已经关闭，因为通道关闭之后，还有些事件会依然继续处理
        if( channel.isActive() ){
            protocolProcessor.pushMsg(channel);
        }
    }


    public void processConnectionLost(MqttNettyChannel channel) {
        if(null == channel.getClientId()){
            logger.error("clientId为空");
            channel.close();
        }
        protocolProcessor.processConnectionLost(channel);
    }


    public void processConnectionException(MqttNettyChannel channel) {
        if(null == channel.getClientId()){
            logger.error("clientId为空");
            channel.close();
        }
        protocolProcessor.processConnectionException(channel);
    }


    public void notifyChannelWritabilityChanged(MqttNettyChannel channel) {
        if(null == channel.getClientId()){
            logger.error("clientId为空");
            channel.close();
        }
        protocolProcessor.processConnectionException(channel);
    }

    public ProtocolProcessor getProtocolProcessor() {
        return protocolProcessor;
    }

    public void setProtocolProcessor(ProtocolProcessor protocolProcessor) {
        this.protocolProcessor = protocolProcessor;
    }
}
