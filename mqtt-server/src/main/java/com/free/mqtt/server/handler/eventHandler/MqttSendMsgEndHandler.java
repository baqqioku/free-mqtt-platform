package com.free.mqtt.server.handler.eventHandler;

import com.free.mqtt.MqttServer;
import com.free.mqtt.server.concurrent.AsyncThreadPoolExecutor;
import com.free.mqtt.server.event.MqttSendMsgEndEvent;
import com.free.mqtt.server.handler.MqttBaseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


//消息发送成功或者是响应成功之后的处理器
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

        asyncThreadPoolExcecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mqttServer.getMqttMsgListener().notifySendMsgOk(mqttSendMsgEndEvent);
                } catch (Exception e) {
                    logger.error("Notify send msg ok failed for clientId: {}", mqttSendMsgEndEvent.getClientId(), e);
                }
            }
        });
    }
}
