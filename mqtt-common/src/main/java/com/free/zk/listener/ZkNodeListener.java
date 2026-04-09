package com.free.zk.listener;

import com.alibaba.fastjson.JSON;
import com.free.zk.ClusterServerMonitor;
import com.free.zk.IZkNodeListener;
import com.free.zk.ZookeeperClient;
import com.free.zk.core.ClusterInfo;
import com.free.zk.core.ServerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ZkNodeListener implements IZkNodeListener {

    private ZookeeperClient zookeeperClient;

    private ClusterServerMonitor clusterServerMonitor;

    public ZkNodeListener(ZookeeperClient zookeeperClient,ClusterServerMonitor clusterServerMonitor){
        this.zookeeperClient = zookeeperClient;
        this.clusterServerMonitor = clusterServerMonitor;
    }


    @Override
    public void notify(String rootPath, List<String> childs) {
        ClusterInfo clusterInfo = clusterServerMonitor.getClusterInfo();
        List<ServerInfo> serverInfoList = new ArrayList<>();
        Map<String, ServerInfo> newBrokerMap = new ConcurrentHashMap<>();
        
        if (childs != null) {
            for(String node : childs){
                String data = zookeeperClient.getData(rootPath + "/" + node);
                if(null == data){
                    continue;
                }

                ServerInfo temp = JSON.parseObject(data, ServerInfo.class);
                if (temp != null) {
                    serverInfoList.add(temp);
                    if (temp.getBrokerName() != null) {
                        newBrokerMap.put(temp.getBrokerName(), temp);
                    }
                }
            }
        }
        clusterInfo.setServerInfoList(serverInfoList);
        clusterInfo.setBrokerMap(newBrokerMap);
        clusterInfo.setRefreshTime(System.currentTimeMillis());
    }

    @Override
    public void notifyDataChange(String dataPath, Object serverData) {
        ServerInfo temp = JSON.parseObject((String) serverData, ServerInfo.class);
        ClusterInfo clusterInfo = clusterServerMonitor.getClusterInfo();
        if (clusterInfo == null) {
            return;
        }

        // Create new list to avoid ConcurrentModificationException
        List<ServerInfo> oldList = clusterInfo.getServerInfoList();
        List<ServerInfo> newList = (oldList == null) ? new ArrayList<>() : new ArrayList<>(oldList);
        newList.removeIf(serverInfo -> Objects.equals(temp.getBrokerName(), serverInfo.getBrokerName()));
        newList.add(temp);
        
        // Create new map to ensure atomicity
        Map<String, ServerInfo> oldMap = clusterInfo.getBrokerMap();
        Map<String, ServerInfo> newMap = (oldMap == null) ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(oldMap);
        newMap.put(temp.getBrokerName(), temp);

        clusterInfo.setServerInfoList(newList);
        clusterInfo.setBrokerMap(newMap);
        clusterInfo.setRefreshTime(System.currentTimeMillis());
    }

    @Override
    public void notifyDataDeleted(String dataPath) {
        String brokerName = dataPath.substring(dataPath.lastIndexOf("/") + 1);
        ClusterInfo clusterInfo = clusterServerMonitor.getClusterInfo();
        if (clusterInfo == null) {
            return;
        }

        // Create new list
        List<ServerInfo> oldList = clusterInfo.getServerInfoList();
        if (oldList != null) {
            List<ServerInfo> newList = new ArrayList<>(oldList);
            newList.removeIf(serverInfo -> Objects.equals(brokerName, serverInfo.getBrokerName()));
            clusterInfo.setServerInfoList(newList);
        }

        // Create new map
        Map<String, ServerInfo> oldMap = clusterInfo.getBrokerMap();
        if (oldMap != null) {
            Map<String, ServerInfo> newMap = new ConcurrentHashMap<>(oldMap);
            newMap.remove(brokerName);
            clusterInfo.setBrokerMap(newMap);
        }
        
        clusterInfo.setRefreshTime(System.currentTimeMillis());
    }
}
