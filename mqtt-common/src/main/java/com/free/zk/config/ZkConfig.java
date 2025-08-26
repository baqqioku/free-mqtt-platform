package com.free.zk.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("zkConfig")
public class ZkConfig {

    private String address;

    private int sessionTimeout;

    private int connectionTimeout;

    public ZkConfig() {
    }

    public ZkConfig(String address, int sessionTimeout, int connectionTimeout) {
        this.address = address;
        this.sessionTimeout = sessionTimeout;
        this.connectionTimeout = connectionTimeout;
    }

    public String getAddress() {
        return address;
    }

    @Value("${zk.address:}")
    public void setAddress(String address) {
        this.address = address;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    @Value("${zk.sessionTimeout:60000}")
    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @Value("${zk.connectionTimeout:15000}")
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }


}

