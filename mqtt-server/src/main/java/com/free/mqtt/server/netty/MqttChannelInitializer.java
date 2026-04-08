package com.free.mqtt.server.netty;

import com.free.mqtt.server.handler.MqttServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;

public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {
    
    private final MqttServerHandler mqttServerHandler;
    
    public MqttChannelInitializer(MqttServerHandler mqttServerHandler) {
        this.mqttServerHandler = mqttServerHandler;
    }
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline()
                .addLast("idleStateHandler", new IdleStateHandler(60, 0, 0))
                .addLast("decoder", new MqttDecoder())
                .addLast("encoder", MqttEncoder.INSTANCE)
                .addLast("handler", mqttServerHandler);
    }
}
