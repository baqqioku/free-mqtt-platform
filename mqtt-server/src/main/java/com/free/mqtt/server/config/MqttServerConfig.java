package com.free.mqtt.server.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


@Configuration
public class MqttServerConfig {

    @Value("${tcpPort:1883}")
    private int tcpPort;

    @Value("${tcpSslTcpPort:8883}")
    private int tcpSslTcpPort;

    @Value("${mqtt.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${mqtt.ssl.keystore.path:}")
    private String sslKeystorePath;

    @Value("${mqtt.ssl.keystore.password:}")
    private String sslKeystorePassword;

    @Value("${mqtt.ssl.keystore.type:JKS}")
    private String sslKeystoreType;

    @Value("${mqtt.ssl.needClientAuth:false}")
    private boolean sslNeedClientAuth;

    @Value("${mqtt.acl.enabled:false}")
    private boolean aclEnabled;

    @Value("${mqtt.acl.file:}")
    private String aclFile;

    @Value("${mqtt.acl.defaultAllow:true}")
    private boolean aclDefaultAllow;

    @Value("${mqtt.acl.reloadSeconds:30}")
    private int aclReloadSeconds;

    @Value("${mqtt.will.delaySeconds:0}")
    private int willDelaySeconds;

    @Value("${cluster.enabled:true}")
    private boolean clusterEnabled;

    @Value("${cluster.local.brokerName:broker-local}")
    private String clusterLocalBrokerName;

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

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getSslKeystorePath() {
        return sslKeystorePath;
    }

    public void setSslKeystorePath(String sslKeystorePath) {
        this.sslKeystorePath = sslKeystorePath;
    }

    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    public void setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
    }

    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    public void setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
    }

    public boolean isSslNeedClientAuth() {
        return sslNeedClientAuth;
    }

    public void setSslNeedClientAuth(boolean sslNeedClientAuth) {
        this.sslNeedClientAuth = sslNeedClientAuth;
    }

    public boolean isAclEnabled() {
        return aclEnabled;
    }

    public void setAclEnabled(boolean aclEnabled) {
        this.aclEnabled = aclEnabled;
    }

    public String getAclFile() {
        return aclFile;
    }

    public void setAclFile(String aclFile) {
        this.aclFile = aclFile;
    }

    public boolean isAclDefaultAllow() {
        return aclDefaultAllow;
    }

    public void setAclDefaultAllow(boolean aclDefaultAllow) {
        this.aclDefaultAllow = aclDefaultAllow;
    }

    public int getAclReloadSeconds() {
        return aclReloadSeconds;
    }

    public void setAclReloadSeconds(int aclReloadSeconds) {
        this.aclReloadSeconds = aclReloadSeconds;
    }

    public int getWillDelaySeconds() {
        return willDelaySeconds;
    }

    public void setWillDelaySeconds(int willDelaySeconds) {
        this.willDelaySeconds = willDelaySeconds;
    }

    public boolean isClusterEnabled() {
        return clusterEnabled;
    }

    public void setClusterEnabled(boolean clusterEnabled) {
        this.clusterEnabled = clusterEnabled;
    }

    public String getClusterLocalBrokerName() {
        return clusterLocalBrokerName;
    }

    public void setClusterLocalBrokerName(String clusterLocalBrokerName) {
        this.clusterLocalBrokerName = clusterLocalBrokerName;
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
