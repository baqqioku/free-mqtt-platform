package com.free.web;

import com.free.common.resp.BaseResponse;
import com.free.mqtt.MqttServer;
import com.free.mqtt.server.session.data.ClientSession;
import com.free.mqtt.server.subscriptions.data.Subscription;
import com.free.mqtt.server.subscriptions.data.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin API Controller (MQTT Server本地)
 * 只提供本机session数据和stats，客户端/集群管理走Route服务
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private MqttServer mqttServer;

    private boolean isSessionConnected(ClientSession session) {
        try {
            if (session.getChannel() == null || session.getChannel().getChannel() == null) {
                return false;
            }
            return session.getChannel().isActive();
        } catch (Exception ex) {
            return false;
        }
    }

    @GetMapping("/clients")
    public BaseResponse<List<Map<String, Object>>> getClients() {
        try {
            Map<String, ClientSession> sessions = mqttServer.getSessionRepository().getSessionsCache();
            List<Map<String, Object>> clients = new ArrayList<>();

            for (Map.Entry<String, ClientSession> entry : sessions.entrySet()) {
                ClientSession session = entry.getValue();
                Map<String, Object> client = new HashMap<>();
                client.put("clientId", entry.getKey());
                client.put("connected", isSessionConnected(session));
                client.put("cleanSession", session.isCleanSession());
                client.put("connectTime", session.getConnectTime());
                client.put("userName", session.getUserName() != null ? session.getUserName() : "");

                // 订阅信息
                List<Map<String, Object>> subs = new ArrayList<>();
                try {
                    Map<Topic, Subscription> subscriptions = session.getSubscriptions();
                    if (subscriptions != null) {
                        for (Map.Entry<Topic, Subscription> subEntry : subscriptions.entrySet()) {
                            Map<String, Object> subInfo = new HashMap<>();
                            subInfo.put("topic", subEntry.getKey().toString());
                            Subscription sub = subEntry.getValue();
                            subInfo.put("qos", sub.getQos() != null ? sub.getQos().value() : 0);
                            subs.add(subInfo);
                        }
                    }
                } catch (Exception ignored) {}
                client.put("subscriptions", subs);
                client.put("subscriptionCount", subs.size());

                clients.add(client);
            }

            return BaseResponse.success(clients);
        } catch (Exception e) {
            logger.error("Failed to get clients", e);
            return BaseResponse.fail("Failed to get clients: " + e.getMessage());
        }
    }

    @GetMapping("/messages")
    public BaseResponse<List<Map<String, Object>>> getMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return BaseResponse.success(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Failed to get messages", e);
            return BaseResponse.fail("Failed to get messages: " + e.getMessage());
        }
    }

    /**
     * 获取系统统计信息
     * 包括：客户端数、在线状态、消息统计等
     */
    @GetMapping("/stats")
    public BaseResponse<Map<String, Object>> getStats() {
        try {
            Map<String, ClientSession> sessions = mqttServer.getSessionRepository().getSessionsCache();
            Map<String, Object> stats = new HashMap<>();
            
            // 基础统计
            long onlineCount = sessions.values().stream()
                    .filter(this::isSessionConnected).count();
            stats.put("totalClients", sessions.size());
            stats.put("onlineClients", onlineCount);
            
            // 消息统计（当前版本暂未实现，返回默认值）
            // 实际应该从消息队列或存储中计算
            stats.put("messagesToday", 0);
            stats.put("messageRate", 0.0);
            
            // 系统运行时间（毫秒）
            // 这里使用启动时间作为参考，实际需要在 MqttServer 中记录启动时间
            stats.put("uptime", calculateUptime());
            
            stats.put("timestamp", System.currentTimeMillis());
            stats.put("clusterEnabled", mqttServer != null);
            
            return BaseResponse.success(stats);
        } catch (Exception e) {
            logger.error("Failed to get stats", e);
            return BaseResponse.fail("Failed to get stats: " + e.getMessage());
        }
    }
    
    /**
     * 计算系统运行时间（小时）
     */
    private long calculateUptime() {
        try {
            // 获取 Java 进程运行时间（毫秒）
            long uptimeMillis = java.lang.management.ManagementFactory
                    .getRuntimeMXBean().getUptime();
            // 转换为小时
            return uptimeMillis / 3600000;
        } catch (Exception e) {
            logger.debug("Failed to calculate uptime", e);
            return 0;
        }
    }
}
