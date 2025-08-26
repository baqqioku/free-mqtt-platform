package com.free.mqtt.server.auth;

import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.qos.ProtocolProcessor;

public class AuthChannel {

    private String clientId;

    private String userName;

    private String passWord;

    private MqttNettyChannel authChannel;

    private ProtocolProcessor protocolProcessor;

    public AuthChannel(String clientId, String userName, String passWord, MqttNettyChannel authChannel) {
        this.clientId = clientId;
        this.userName = userName;
        this.passWord = passWord;
        this.authChannel = authChannel;
    }

    public AuthChannel(String clientId, String userName, String passWord, MqttNettyChannel authChannel, ProtocolProcessor protocolProcessor) {
        this.clientId = clientId;
        this.userName = userName;
        this.passWord = passWord;
        this.authChannel = authChannel;
        this.protocolProcessor = protocolProcessor;
    }

    public void authFail(){
        authChannel.setAuth(false);

        protocolProcessor.auth(this);
    }


    public void authSuccess(){
        authChannel.setAuth(true);

        protocolProcessor.auth(this);
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassWord() {
        return passWord;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }

    public MqttNettyChannel getAuthChannel() {
        return authChannel;
    }

    public void setAuthChannel(MqttNettyChannel authChannel) {
        this.authChannel = authChannel;
    }

    public ProtocolProcessor getProtocolProcessor() {
        return protocolProcessor;
    }

    public void setProtocolProcessor(ProtocolProcessor protocolProcessor) {
        this.protocolProcessor = protocolProcessor;
    }
}
