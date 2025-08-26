package com.free.mqtt.server.utils;

import com.free.mqtt.server.netty.MqttNettyChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

public class MqttNettyUtils {

    private static final AttributeKey<Object> ATTR_KEY_NETTY_CHAANEL = AttributeKey.valueOf("clientSession");

    public static Object getAttribute(ChannelHandlerContext ctx,AttributeKey<Object> key){
        Attribute<Object> attr = ctx.channel().attr(key);
        return attr.get();
    }

    public static void channel(MqttNettyChannel nettyChannel, Channel channel){
        channel.attr(ATTR_KEY_NETTY_CHAANEL).set(nettyChannel);
    }

    public static MqttNettyChannel channel(Channel channel){
        return (MqttNettyChannel) channel.attr(ATTR_KEY_NETTY_CHAANEL).get();
    }


}
