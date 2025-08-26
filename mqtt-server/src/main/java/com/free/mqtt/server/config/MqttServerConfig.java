package com.free.mqtt.server.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


@Configuration
public class MqttServerConfig {

    @Value("${tcpPort:1883}")
    private int tcpPort;

    @Value("${tcpSslTcpPort:8883}")
    private int tcpSslTcpPort;

    private int httpWebSocketPort;

    private int httpsWebSocketPort;

    private int clusterPort;

    //过滤topic
    private String filterTopic;


    public int getTcpPort() {
        return tcpPort;
    }


    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public int getTcpSslTcpPort() {
        return tcpSslTcpPort;
    }

    public void setTcpSslTcpPort(int tcpSslTcpPort) {
        this.tcpSslTcpPort = tcpSslTcpPort;
    }

    public int getHttpWebSocketPort() {
        return httpWebSocketPort;
    }

    public void setHttpWebSocketPort(int httpWebSocketPort) {
        this.httpWebSocketPort = httpWebSocketPort;
    }

    public int getHttpsWebSocketPort() {
        return httpsWebSocketPort;
    }

    public void setHttpsWebSocketPort(int httpsWebSocketPort) {
        this.httpsWebSocketPort = httpsWebSocketPort;
    }

    public int getClusterPort() {
        return clusterPort;
    }

    public void setClusterPort(int clusterPort) {
        this.clusterPort = clusterPort;
    }

    public String getFilterTopic() {
        return filterTopic;
    }

    public void setFilterTopic(String filterTopic) {
        this.filterTopic = filterTopic;
    }


}
