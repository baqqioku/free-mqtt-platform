package com.free.mqtt.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class MqttNettyChannel {

    private static final Logger logger = LoggerFactory.getLogger(MqttNettyChannel.class);

    private String clientId;

    private Channel channel;

    private boolean isHaveSendEvent = false;

    protected boolean isAuth = false;

    public MqttNettyChannel(Channel channel){
        this.channel = channel;
    }

    public boolean writeAndFlush(Object msg) throws Exception{

        try{
            if( !channel.isActive() ){
                return false;
            }

            ChannelFuture future = channel.writeAndFlush(msg);
            //return future.sync().isSuccess();

            return true;
        }catch(Exception e){
            logger.error("发送消息失败", e);

            throw e;
        }
    }


    public void close() {
        channel.close();
    }

    public String remoteAddr(){
        return getSocketAddress().getAddress().getHostAddress();
    }


    public InetSocketAddress getSocketAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }


    public boolean isActive() {
        return channel.isActive();
    }


    public void fireEvent(Object event) {
        if(isActive()){
            channel.pipeline().fireUserEventTriggered(event);
        }
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public boolean isHaveSendEvent() {
        return isHaveSendEvent;
    }

    public void setHaveSendEvent(boolean haveSendEvent) {
        isHaveSendEvent = haveSendEvent;
    }

    public boolean isAuth() {
        return isAuth;
    }

    public void setAuth(boolean auth) {
        isAuth = auth;
    }
}
