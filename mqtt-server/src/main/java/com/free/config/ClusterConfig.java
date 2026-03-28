package com.free.config;

import com.free.zk.config.ZkConfig;
import com.free.zk.ClusterServerMonitor;
import com.free.zk.core.LocalClusterServerMonitor;
import com.free.zk.core.ZkClusterServerMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

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
                                                          @Value("${cluster.local.brokerName:broker-local}") String brokerName) {
        return new LocalClusterServerMonitor(clusterName, Collections.emptyList(), brokerName);
    }
}
