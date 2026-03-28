package com.free.mqtt.server.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.mqtt.MqttMessage;

import java.util.List;

public class MqttWebSocketCodec extends MessageToMessageCodec<BinaryWebSocketFrame, ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        out.add(new BinaryWebSocketFrame(msg.retain()));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BinaryWebSocketFrame msg, List<Object> out) throws Exception {
        out.add(msg.content().retain());
    }
}
