package com.free.web;


import com.alibaba.fastjson.JSON;
import com.free.zk.core.ServerInfo;
import com.free.zk.core.ZkClusterServerMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClusterTest {

    @Autowired
    ZkClusterServerMonitor zkClusterServerMonitor;

    @RequestMapping("/test1")
    public void test1(){
        //ZkConfig zkConfig = new ZkConfig("127.0.0.1",60000,15000);
        /*ServerInfo serverInfo = new ServerInfo();
        serverInfo.setBrokerName("broker-1");
        serverInfo.setIp("127.0.0.1");
        serverInfo.setPort(23236);*/
        ServerInfo serverInfo = zkClusterServerMonitor.initServerInfo(1900,8884);
        zkClusterServerMonitor.register(serverInfo);
        zkClusterServerMonitor.monitor();

    }


    @RequestMapping("/test2")
    public void test2(){
      /*  ZkConfig zkConfig = new ZkConfig("127.0.0.1",60000,15000);
        ZookeeperClient zookeeperClient = new ZookeeperClient(zkConfig);
*/
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setBrokerName("broker-1");
        serverInfo.setIp("127.0.0.1");
        serverInfo.setTcpPort(1553);
        zkClusterServerMonitor.writeData("/free/mqtt/broker-1", JSON.toJSONString(serverInfo));
    }
}
