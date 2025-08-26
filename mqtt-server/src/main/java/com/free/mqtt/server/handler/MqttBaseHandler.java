package com.free.mqtt.server.handler;

import com.free.mqtt.MqttServer;
import com.free.mqtt.server.concurrent.AsyncThreadPoolExecutor;
import com.free.mqtt.server.event.MqttBaseEvent;
import com.free.mqtt.server.session.data.ClientSession;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class MqttBaseHandler<T extends MqttBaseEvent> implements Handler<T> {

    private static Logger logger = LoggerFactory.getLogger(MqttBaseHandler.class);

    protected MqttServer mqttServer;

    protected AsyncThreadPoolExecutor asyncThreadPoolExcecutor;

    protected MqttBaseHandler(AsyncThreadPoolExecutor asyncThreadPoolExcecutor, MqttServer mqttServer){
        this.asyncThreadPoolExcecutor = asyncThreadPoolExcecutor;
        this.mqttServer = mqttServer;
    }

    public boolean ifSessionInvalid(MqttBaseEvent event){
        String clientId = event.getClientId();

        if(null == mqttServer.getSessionRepository().getSession(clientId)){
            return true;
        }

        return false;
    }

    public ClientSession getClientSession(MqttBaseEvent event){
        return mqttServer.getSessionRepository().getSession(event.getClientId());
    }


}
