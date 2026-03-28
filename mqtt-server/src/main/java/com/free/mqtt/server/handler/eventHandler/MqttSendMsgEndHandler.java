package com.free.mqtt.server.handler.eventHandler;

import com.free.mqtt.MqttServer;
import com.free.mqtt.server.concurrent.AsyncThreadPoolExecutor;
import com.free.mqtt.server.event.MqttSendMsgEndEvent;
import com.free.mqtt.server.handler.MqttBaseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


//消息发送成功之后的处理器
public class MqttSendMsgEndHandler extends MqttBaseHandler<MqttSendMsgEndEvent> {

    public static Logger logger = LoggerFactory.getLogger(MqttSendMsgEndHandler.class);


    public MqttSendMsgEndHandler(AsyncThreadPoolExecutor asyncThreadPoolExcecutor, MqttServer mqttServer) {
        super(asyncThreadPoolExcecutor, mqttServer);
    }

    @Override
    public void handle(MqttSendMsgEndEvent mqttSendMsgEndEvent) {
        if( ifSessionInvalid(mqttSendMsgEndEvent) ){
            logger.error("mqtt消息中心会话已经失效   clientId:{}", mqttSendMsgEndEvent.getClientId());
            return;
        }

        asyncThreadPoolExcecutor.submit(new java.util.concurrent.FutureTask<Object>(new Runnable() {
            @Override
            public void run() {
                // No-op placeholder for send-ack callback.
            }
        }, null));
    }
}
