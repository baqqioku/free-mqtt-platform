package com.free.mqtt.server.netty;

import com.free.mqtt.server.config.MqttServerConfig;
import com.free.mqtt.server.netty.handler.MqttProcessHandler;
import com.free.mqtt.server.qos.ProtocolProcessor;

public abstract class AbstractMqttServer {

    protected MqttServerConfig serverConfig;

    //protected ProtocolProcessor protocolProcessor;

    private MqttProcessHandler mqttProcessHandler;

    public AbstractMqttServer(MqttServerConfig serverConfig, MqttProcessHandler mqttProcessHandler) {
        this.serverConfig = serverConfig;
        this.mqttProcessHandler = mqttProcessHandler;
    }

    public AbstractMqttServer(MqttServerConfig mqttServerConfig){
        this.serverConfig = mqttServerConfig;
    }

    public MqttServerConfig getServerConfig() {
        return serverConfig;
    }

    public void setServerConfig(MqttServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public MqttProcessHandler getMqttProcessHandler() {
        return mqttProcessHandler;
    }

    public void setMqttProcessHandler(MqttProcessHandler mqttProcessHandler) {
        this.mqttProcessHandler = mqttProcessHandler;
    }

    public abstract void init() throws Exception;

    public abstract void stop();
}
