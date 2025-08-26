package com.free.zk.core;

import com.alibaba.fastjson.JSON;
import com.free.zk.ClusterServerMonitor;
import com.free.zk.IZkNodeListener;
import com.free.zk.ZookeeperClient;
import com.free.zk.config.ZkConfig;
import com.free.zk.event.MonitorEvent;
import com.free.zk.event.RegisterEvent;
import com.free.zk.event.UnMonitorEvent;
import com.free.zk.listener.ZkNodeListener;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ZkClusterServerMonitor extends ClusterServerMonitor {

    private ZookeeperClient zookeeperClient;


    String path= "";


    public ZkClusterServerMonitor(ZkConfig zkConfig, String clusterName){
        super(clusterName);
        if (clusterName != null && !clusterName.isEmpty()) {
            path = MQTT_CLUSTER_PATH  + "/"+ clusterName;
        } else {
            path = MQTT_CLUSTER_PATH + "/" + defaultClusterName;
        }
        zookeeperClient  = new ZookeeperClient(zkConfig);
    }

    @Override
    public boolean register(ServerInfo data) {
        boolean rtv =true;
        try {
            rtv = zookeeperClient.createNode(path,data.getBrokerName(), JSON.toJSONString(data));
        }catch (Exception e){
            logger.error("创建监控节点异常  ", clusterName);

            RegisterEvent zkEvent = new RegisterEvent();
            zkEvent.setClusterName(clusterName);
            zkEvent.setServerInfo(data);
            addZkEvent(zkEvent);
        }
        return rtv;
    }

    @Override
    public void unMonitor() {
        remove();

        try {
            zookeeperClient.cancelMonitor(path);
        } catch (Exception e) {
            logger.error("取消订阅监控节点异常  ", clusterName);

            UnMonitorEvent zkEvent = new UnMonitorEvent();
            zkEvent.setClusterName(clusterName);
            addZkEvent(zkEvent);
        }
    }

    @Override
    public boolean monitor() {
        boolean rtv = true;
        IZkNodeListener listener = new ZkNodeListener(zookeeperClient,this);
        try {
            ClusterInfo clusterInfo = getClusterInfo();
            if(null == clusterInfo){
                addClusterInfo();
            }
            rtv = zookeeperClient.nodeMonitor(path, listener);
        }catch (Exception e){
            logger.error("订阅监控节点异常  ", clusterName);
            MonitorEvent zkEvent = new MonitorEvent();
            zkEvent.setClusterName(clusterName);
            zkEvent.setiZkNodeListener(listener);
            addZkEvent(zkEvent);
        }
        return rtv;
    }

    @Override
    public List<ServerInfo> getData() {

        List<String> dataList = zookeeperClient.getChildsData(path);
        List<ServerInfo> serverInfoList = new ArrayList<ServerInfo>();

        if(dataList != null && dataList.size()>0){
            for(String data : dataList){
                if(data == null) continue;
                ServerInfo temp = JSON.parseObject(data,ServerInfo.class);
                serverInfoList.add(temp);
            }
        }
        return serverInfoList;
    }

    @Override
    public ServerInfo initServerInfo(int tcpPort,int httpPort) {
        ServerInfo serverInfo = new ServerInfo();

        // 1. 获取 brokerName 编号
        int brokerId = zookeeperClient.getNextBrokerId(path);
        serverInfo.setBrokerName("broker-" + brokerId);

        // 2. 获取 IP（适配容器 / 虚拟机）
        serverInfo.setIp(getLocalIpAddress());

        // 3. 设置端口
        serverInfo.setTcpPort(tcpPort);
        serverInfo.setHttpPort(httpPort);

        return serverInfo;
    }



    /**
     * 获取本机 IPv4 地址，适配容器/虚拟机
     */
    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(":") == -1) {
                        return addr.getHostAddress(); // 找到第一个 IPv4
                    }
                }
            }
            // 兜底方案
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            e.printStackTrace();
            return "127.0.0.1";
        }
    }

    @Override
    public boolean writeData(String path,String data){
        boolean rtv = true;
        try {
             zookeeperClient.writeData(path, data);
        }catch (Exception e){
            logger.error("写入数据失败",e);
            return false;
        }
        return rtv;
    }

}
