package com.free.mqtt.test;

import com.alibaba.fastjson.JSON;
import com.free.zk.*;
import com.free.zk.config.ZkConfig;
import com.free.zk.core.ServerInfo;
import com.free.zk.core.ZkClusterServerMonitor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

public class MqttClusterTest {

    //@Test
    public static void main(String[] args) throws InterruptedException {

        ZkConfig zkConfig = new ZkConfig("127.0.0.1",60000,15000);
        ZkClusterServerMonitor clusterServerMonitor = (ZkClusterServerMonitor) ZkClusterServerMonitor.createClusterServerMonitor(zkConfig,"guoguo");
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setBrokerName("broker-1");
        serverInfo.setIp("127.0.0.1");
        serverInfo.setTcpPort(23236);
        clusterServerMonitor.register(serverInfo);
        clusterServerMonitor.monitor();

        // 模拟业务运行期间更新节点数据
        Thread.sleep(2000);
        serverInfo.setTcpPort(18882);
        clusterServerMonitor.writeData("/free/mqtt/free/broker-1", JSON.toJSONString(serverInfo));

        // ===== 保活机制 =====
        // 让 main 方法保持运行，保证 zkClient 会话不断开，临时节点不会被删除
        new CountDownLatch(1).await();
    }

    @Test
    public void test2(){

        ZkConfig zkConfig = new ZkConfig("127.0.0.1",60000,15000);
        ZookeeperClient zookeeperClient = new ZookeeperClient(zkConfig);

        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setBrokerName("broker-1");
        serverInfo.setIp("127.0.0.1");
        serverInfo.setTcpPort(1553);
        zookeeperClient.writeData("/free/mqtt/free/broker-1", JSON.toJSONString(serverInfo));

        //clusterServerMonitor.unMonitor();
    }
}
