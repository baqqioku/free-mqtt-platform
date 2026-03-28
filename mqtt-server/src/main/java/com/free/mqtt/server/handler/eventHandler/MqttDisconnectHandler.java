package com.free.mqtt.server.handler.eventHandler;

import com.free.mqtt.MqttServer;
import com.free.mqtt.server.concurrent.AsyncThreadPoolExecutor;
import com.free.mqtt.server.event.MqttDisconnectEvent;
import com.free.mqtt.server.handler.MqttBaseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttDisconnectHandler extends MqttBaseHandler<MqttDisconnectEvent> {

    private static final Logger logger = LoggerFactory.getLogger(MqttDisconnectHandler.class);

    public MqttDisconnectHandler(AsyncThreadPoolExecutor asyncThreadPoolExecutor, MqttServer mqttServer) {
        super(asyncThreadPoolExecutor, mqttServer);
    }

    @Override
    public void handle(MqttDisconnectEvent event) {
        asyncThreadPoolExcecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mqttServer.getMqttMsgListener().notifyDisconnect(event.getClientId());
                } catch (Exception e) {
                    logger.error("Notify disconnect failed for clientId: {}", event.getClientId(), e);
                }
            }
        });
    }
}
