package com.free.route.service.impl;

import com.free.common.algorithm.ConsistentHashRing;
import com.free.route.service.RouteService;
import com.free.route.vo.MqttServerVo;
import com.free.zk.core.ClusterInfo;
import com.free.zk.core.ServerInfo;
import com.free.zk.core.ZkClusterServerMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

import static com.free.common.constant.RedisKeyConstant.USER_BROKER;

@Service
public class RouteServiceImpl implements RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteServiceImpl.class);

    @Autowired
    private ZkClusterServerMonitor zkClusterServerMonitor;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Override
    public MqttServerVo lbsServer(Long userId) {

        ClusterInfo clusterInfo = zkClusterServerMonitor.getClusterInfo();

        if(clusterInfo == null || clusterInfo.getServerInfoList() == null || clusterInfo.getServerInfoList().size()<=0){
            logger.error("集群信息为空");
            return null;
        }

        Map<String, ServerInfo> serverInfoMap =  clusterInfo.getBrokerMap();
        ConsistentHashRing<String> consistentHashRing = new ConsistentHashRing<String>(160,serverInfoMap.keySet());
        String brokerName = consistentHashRing.getNode(userId.toString());

        ServerInfo serverInfo = serverInfoMap.get(brokerName);
        if(serverInfo == null){
            return null;
        }

        String clientId = UUID.randomUUID().toString().replaceAll("-", "")+"_"+userId;
        MqttServerVo mqttServer = new MqttServerVo();
        mqttServer.setBrokerName(brokerName);
        mqttServer.setClientId(clientId);
        mqttServer.setIp(serverInfo.getIp());
        mqttServer.setTcpPort(serverInfo.getTcpPort());
        mqttServer.setHttpPort(serverInfo.getHttpPort());
        return mqttServer;
    }

    @Override
    public MqttServerVo findUserBroker(Long userId) {
        String brokerName = redisTemplate.opsForValue().get(USER_BROKER+userId);
        MqttServerVo mqttServerVo = new MqttServerVo();
        if(brokerName != null){
            ClusterInfo clusterInfo = zkClusterServerMonitor.getClusterInfo();
            if(clusterInfo == null || clusterInfo.getServerInfoList() == null || clusterInfo.getServerInfoList().size()<=0){
                logger.error("集群信息为空");
                return null;
            }
            ServerInfo serverInfo = clusterInfo.getBrokerMap().get(brokerName);
            mqttServerVo.setHttpPort(serverInfo.getHttpPort());
            mqttServerVo.setIp(serverInfo.getIp());
        }
        return mqttServerVo;
    }
}
