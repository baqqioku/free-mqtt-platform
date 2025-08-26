package com.free.route.config;


import com.free.zk.config.ZkConfig;
import com.free.zk.core.ZkClusterServerMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClusterConfig {

    @Bean
     public ZkClusterServerMonitor zkClusterServerMonitor(@Autowired ZkConfig zkConfig, @Value("${clusterName:}") String clusterName){
        return (ZkClusterServerMonitor) ZkClusterServerMonitor.createClusterServerMonitor(zkConfig,clusterName);
    }
}
