package com.free.zk.core;

import java.io.Serializable;

public class ServerInfo implements Serializable {

    private String brokerName;

    private String ip;

    private int tcpPort;

    private int httpPort;

    public ServerInfo() {
    }

    public ServerInfo(String brokerName, String ip, int tcpPort, int httpPort) {
        this.brokerName = brokerName;
        this.ip = ip;
        this.tcpPort = tcpPort;
        this.httpPort = httpPort;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }



    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public String getBrokerName() {
        return brokerName;
    }

    public void setBrokerName(String brokerName) {
        this.brokerName = brokerName;
    }

    @Override
    public String toString() {
        return "ServerInfo{" +
                "brokerName='" + brokerName + '\'' +
                ", ip='" + ip + '\'' +
                ", tcpPort=" + tcpPort +
                ", httpPort=" + httpPort +
                '}';
    }
}
