package com.free.route.service.impl;

import com.free.common.algorithm.ConsistentHashRing;
import com.free.route.service.RouteService;
import com.free.route.vo.MqttServerVo;
import com.free.zk.core.ClusterInfo;
import com.free.zk.core.ServerInfo;
import com.free.zk.ClusterServerMonitor;
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
    private ClusterServerMonitor clusterServerMonitor;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    // 临时黑名单，防止故障报告后 ZK 还没来得及更新，导致再次分配到同一节点
    private final Map<String, Long> brokerBlacklist = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long BLACKLIST_DURATION_MS = 30 * 1000; // 30秒

    @Override
    public MqttServerVo lbsServer(Long userId) {

        ClusterInfo clusterInfo = clusterServerMonitor.getClusterInfo();

        if(clusterInfo == null || clusterInfo.getServerInfoList() == null || clusterInfo.getServerInfoList().size()<=0){
            logger.error("集群信息为空");
            return null;
        }

        Map<String, ServerInfo> serverInfoMap =  new java.util.HashMap<>(clusterInfo.getBrokerMap());
        
        // 过滤黑名单中的节点
        long now = System.currentTimeMillis();
        serverInfoMap.keySet().removeIf(name -> {
            Long downTime = brokerBlacklist.get(name);
            if (downTime != null && now - downTime < BLACKLIST_DURATION_MS) {
                return true;
            }
            brokerBlacklist.remove(name);
            return false;
        });

        if (serverInfoMap.isEmpty()) {
            logger.warn("所有节点均在黑名单中，尝试刷新集群信息并重试一次");
            clusterServerMonitor.refresh();
            serverInfoMap.putAll(clusterServerMonitor.getClusterInfo().getBrokerMap());
        }

        if (serverInfoMap.isEmpty()) {
            logger.error("无可用 Broker 节点");
            return null;
        }

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
        if(brokerName == null){
            return null;
        }

        // 检查原broker是否在黑名单中（故障转移）
        Long downTime = brokerBlacklist.get(brokerName);
        if (downTime != null && System.currentTimeMillis() - downTime < BLACKLIST_DURATION_MS) {
            logger.info("用户{}原broker[{}]在黑名单中，需重新分配", userId, brokerName);
            return null;
        }

        ClusterInfo clusterInfo = clusterServerMonitor.getClusterInfo();
        if(clusterInfo == null || clusterInfo.getServerInfoList() == null || clusterInfo.getServerInfoList().size()<=0){
            logger.error("集群信息为空");
            return null;
        }
        ServerInfo serverInfo = clusterInfo.getBrokerMap().get(brokerName);
        if (serverInfo == null) {
            return null;
        }
        MqttServerVo mqttServerVo = new MqttServerVo();
        mqttServerVo.setBrokerName(brokerName);
        mqttServerVo.setHttpPort(serverInfo.getHttpPort());
        mqttServerVo.setIp(serverInfo.getIp());
        mqttServerVo.setTcpPort(serverInfo.getTcpPort());
        return mqttServerVo;
    }

    @Override
    public void markBrokerDown(String brokerName) {
        if (brokerName == null) return;
        logger.warn("Marking broker as down, adding to blacklist and refreshing cluster info: {}", brokerName);
        brokerBlacklist.put(brokerName, System.currentTimeMillis());
        clusterServerMonitor.refresh();
    }
}
