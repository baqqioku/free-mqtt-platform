package com.free.mqtt.server;

import com.free.mqtt.MqttServer;
import com.free.mqtt.server.concurrent.AsyncThreadPoolExecutor;
import com.free.mqtt.server.event.MqttAuthEvent;
import com.free.mqtt.server.event.MqttBaseEvent;
import com.free.mqtt.server.event.MqttDisconnectEvent;
import com.free.mqtt.server.event.MqttSendMsgEndEvent;
import com.free.mqtt.server.handler.eventHandler.MqttAuthHandler;
import com.free.mqtt.server.handler.eventHandler.MqttDisconnectHandler;
import com.free.mqtt.server.handler.eventHandler.MqttSendMsgEndHandler;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MqttMsgProcessThread extends BaseThread<MqttBaseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(MqttMsgProcessThread.class);

    private final Map<Class<? extends MqttBaseEvent>, Handler<? extends MqttBaseEvent>> handlers = new HashMap<>();

    private final MqttServer mqttServer;

    private final AsyncThreadPoolExecutor asyncThreadPoolExecutor;

    public MqttMsgProcessThread(MqttServer mqttServer, int threadNum) {
        this.mqttServer = mqttServer;
        if (threadNum > 0) {
            asyncThreadPoolExecutor = new AsyncThreadPoolExecutor(threadNum);
        } else {
            asyncThreadPoolExecutor = new AsyncThreadPoolExecutor(0);
        }

        initHandler();
        start();
    }

    private void initHandler() {
        handlers.put(MqttAuthEvent.class, new MqttAuthHandler(asyncThreadPoolExecutor, mqttServer));
        handlers.put(MqttSendMsgEndEvent.class, new MqttSendMsgEndHandler(asyncThreadPoolExecutor, mqttServer));
        handlers.put(MqttDisconnectEvent.class, new MqttDisconnectHandler(asyncThreadPoolExecutor, mqttServer));
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (asyncThreadPoolExecutor != null) {
            asyncThreadPoolExecutor.destroy();
        }
    }

    @Override
    public void doing(MqttBaseEvent event) {
        if (event == null) {
            return;
        }

        try {
            Handler<? extends MqttBaseEvent> handler = handlers.get(event.getClass());
            if (handler == null) {
                logger.error("Unsupported mqtt event type. eventClass={}", event.getClass().getName());
                return;
            }

            @SuppressWarnings("unchecked")
            Handler<MqttBaseEvent> typedHandler = (Handler<MqttBaseEvent>) handler;
            typedHandler.handle(event);
        } catch (Exception e) {
            logger.error("Process mqtt event failed", e);
        }
    }
}
