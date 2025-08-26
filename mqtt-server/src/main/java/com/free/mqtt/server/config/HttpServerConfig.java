package com.free.mqtt.server.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpServerConfig {
    private int httpPort;

    private int sslPort;

    public int getHttpPort() {
        return httpPort;
    }

    @Value("${server.port:8080}")
    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    @Value("${ssl.port:8443}")
    public int getSslPort() {
        return sslPort;
    }

    public void setSslPort(int sslPort) {
        this.sslPort = sslPort;
    }
}
