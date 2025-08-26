package com.free.zk.core;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClusterInfo implements Serializable {

    private String clusterName;

    private long refreshTime = System.currentTimeMillis();// 毫秒

    private List<ServerInfo> serverInfoList;

    private Map<String,ServerInfo> brokerMap = new ConcurrentHashMap<>();

    public ClusterInfo(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public List<ServerInfo> getServerInfoList() {
        return serverInfoList;
    }

    public void setServerInfoList(List<ServerInfo> serverInfoList) {
        this.serverInfoList = serverInfoList;
    }

    public long getRefreshTime() {
        return refreshTime;
    }

    public void setRefreshTime(long refreshTime) {
        this.refreshTime = refreshTime;
    }

    public Map<String, ServerInfo> getBrokerMap() {
        return brokerMap;
    }

    public void setBrokerMap(Map<String, ServerInfo> brokerMap) {
        this.brokerMap = brokerMap;
    }
}
