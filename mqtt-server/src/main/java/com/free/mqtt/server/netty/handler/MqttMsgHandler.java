package com.free.mqtt.server.netty.handler;

import com.free.mqtt.server.event.MqttFlushCacheEvent;
import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.utils.MqttNettyUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class MqttMsgHandler extends SimpleChannelInboundHandler<MqttMessage> {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttMsgHandler.class);

    private MqttProcessHandler mqttProcessHandler;

    public MqttMsgHandler(MqttProcessHandler mqttProcessHandler){
        this.mqttProcessHandler = mqttProcessHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
       
        try{
            if(true == msg.decoderResult().isFailure() ){
                logger.error("发生异常：{}",msg.decoderResult().cause());
                exceptionCaught(ctx, msg.decoderResult().cause());
                ctx.channel().close();
                return;
            }
            mqttProcessHandler.processMsg(getNettyChannel(ctx),msg);
        }catch (Exception e){
            exceptionCaught(ctx,e);
        }

    }

    private MqttNettyChannel getNettyChannel(ChannelHandlerContext ctx){
        MqttNettyChannel nettyChannel = MqttNettyUtils.channel(ctx.channel());
        if(null == nettyChannel){
            nettyChannel = new MqttNettyChannel(ctx.channel());
            MqttNettyUtils.channel(nettyChannel, ctx.channel());
        }

        return nettyChannel;
    }


    /**
     * 客户端主动关闭 TCP 连接（比如 kill 掉进程、主动断网）。
     * @param ctx
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        MqttNettyChannel nettyChannel = MqttNettyUtils.channel(ctx.channel());
        if(null == nettyChannel){
            return;
        }

        logger.info("通道不活跃  channelInactive clientId:{}", nettyChannel.getClientId());

        mqttProcessHandler.processConnectionLost(nettyChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        MqttNettyChannel nettyChannel = MqttNettyUtils.channel(ctx.channel());
        if(null == nettyChannel){
            return;
        }

        logger.info("socket异常,clientId:{}", nettyChannel.getClientId(), cause);

        mqttProcessHandler.processConnectionException(nettyChannel);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        MqttNettyChannel nettyChannel = MqttNettyUtils.channel(ctx.channel());
        if(null == nettyChannel){
            return;
        }

        logger.error("通道异常  channelWritabilityChanged 数据没有发送出去，还往通道中写数据 clientId:{}", nettyChannel.getClientId());

        mqttProcessHandler.notifyChannelWritabilityChanged(nettyChannel);
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        MqttNettyChannel nettyChannel = MqttNettyUtils.channel(ctx.channel());
        if(null == nettyChannel){

            if( evt instanceof MqttFlushCacheEvent){
                logger.info("通道已经关闭 ，但还有消息未推送 clientId:{}", nettyChannel.getClientId());
            }

            if( evt instanceof IdleStateEvent ) {
                IdleStateEvent e = (IdleStateEvent) evt;
                if (e.state() == IdleState.READER_IDLE) {
                    ctx.channel().close();
                }
            }
            return;
        }

        if( evt instanceof MqttFlushCacheEvent ){
            if( ctx.channel().isActive() ){
                mqttProcessHandler.pushMsg(nettyChannel);
            }
        }else if( evt instanceof IdleStateEvent ){
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                logger.info("通道读空闲，避免系统资源浪费，需要关闭通道  clientId:{}", nettyChannel.getClientId());
                mqttProcessHandler.processConnectionLost(nettyChannel);
            } else if (e.state() == IdleState.WRITER_IDLE) {
                logger.info("通道写空闲，需要检查通道是否有数据需要发送  clientId:{}", nettyChannel.getClientId());
            }
        }
    }


}


