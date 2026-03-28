package com.free.route.config;

import com.free.zk.config.ZkConfig;
import com.free.zk.ClusterServerMonitor;
import com.free.zk.core.LocalClusterServerMonitor;
import com.free.zk.core.ServerInfo;
import com.free.zk.core.ZkClusterServerMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ClusterConfig {

    @Bean
    @ConditionalOnProperty(value = "cluster.enabled", havingValue = "true", matchIfMissing = true)
     public ClusterServerMonitor zkClusterServerMonitor(@Autowired ZkConfig zkConfig, @Value("${clusterName:}") String clusterName){
        return ZkClusterServerMonitor.createClusterServerMonitor(zkConfig,clusterName);
    }

    @Bean
    @ConditionalOnProperty(value = "cluster.enabled", havingValue = "false")
    public ClusterServerMonitor localClusterServerMonitor(@Value("${clusterName:}") String clusterName,
                                                          @Value("${route.static.enabled:false}") boolean staticEnabled,
                                                          @Value("${route.static.brokerName:broker-local}") String brokerName,
                                                          @Value("${route.static.host:127.0.0.1}") String host,
                                                          @Value("${route.static.tcpPort:1883}") int tcpPort,
                                                          @Value("${route.static.httpPort:23240}") int httpPort) {
        List<ServerInfo> servers = new ArrayList<>();
        if (staticEnabled) {
            ServerInfo info = new ServerInfo();
            info.setBrokerName(brokerName);
            info.setIp(host);
            info.setTcpPort(tcpPort);
            info.setHttpPort(httpPort);
            servers.add(info);
        }
        return new LocalClusterServerMonitor(clusterName, servers, brokerName);
    }
}
