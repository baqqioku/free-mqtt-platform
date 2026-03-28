package com.free.zk.listener;

import com.alibaba.fastjson.JSON;
import com.free.zk.ClusterServerMonitor;
import com.free.zk.IZkNodeListener;
import com.free.zk.ZookeeperClient;
import com.free.zk.core.ClusterInfo;
import com.free.zk.core.ServerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        List<ServerInfo> serverInfoList = new ArrayList<ServerInfo>();
        clusterInfo.getBrokerMap().clear();
        if (childs != null) {
            for(String node : childs){
                String data = zookeeperClient.getData(rootPath + "/" + node);
                if(null == data){
                    continue;
                }

                ServerInfo temp = JSON.parseObject(data, ServerInfo.class);
                serverInfoList.add(temp);
                if (temp != null && temp.getBrokerName() != null) {
                    clusterInfo.getBrokerMap().put(temp.getBrokerName(), temp);
                }
            }
        }
        clusterInfo.setServerInfoList(serverInfoList);
        clusterInfo.setRefreshTime(System.currentTimeMillis());
    }



    @Override
    public void notifyDataChange(String dataPath, Object serverData) {
        ServerInfo temp = JSON.parseObject((String) serverData, ServerInfo.class);
        ClusterInfo clusterInfo = clusterServerMonitor.clusterInfoMap.get(clusterServerMonitor.clusterName);
        if (clusterInfo == null) {
            return;
        }
        if (clusterInfo.getServerInfoList() == null) {
            clusterInfo.setServerInfoList(new ArrayList<>());
        }
        clusterInfo.getServerInfoList()
                .removeIf(serverInfo -> Objects.equals(temp.getBrokerName(), serverInfo.getBrokerName()));
        clusterInfo.getServerInfoList().add(temp);
        clusterInfo.getBrokerMap().put(temp.getBrokerName(),temp);
        clusterInfo.setRefreshTime(System.currentTimeMillis());
    }

    @Override
    public void notifyDataDeleted(String dataPath) {

        String brokerName = dataPath.substring(dataPath.lastIndexOf("/")+1);
        ClusterInfo clusterInfo = clusterServerMonitor.clusterInfoMap.get(clusterServerMonitor.clusterName);
        if (clusterInfo == null) {
            return;
        }
        if (clusterInfo.getServerInfoList() != null) {
            clusterInfo.getServerInfoList()
                    .removeIf(serverInfo -> Objects.equals(brokerName, serverInfo.getBrokerName()));
        }
        clusterInfo.getBrokerMap().remove(brokerName);
        clusterInfo.setRefreshTime(System.currentTimeMillis());

    }
}
