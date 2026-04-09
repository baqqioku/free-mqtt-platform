package com.free.route.web;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.free.common.constant.MqttConstant;
import com.free.common.constant.StatusEnum;
import com.free.common.resp.BaseResponse;
import com.free.common.utils.TokenUtil;
import com.free.route.ao.LoginAo;
import com.free.route.ao.PushMsgAo;
import com.free.route.ao.UserAo;
import com.free.route.service.AccountService;
import com.free.route.service.OfflineStoreService;
import com.free.route.service.RouteService;
import com.free.route.vo.LoginReqVO;
import com.free.route.vo.MqttServerVo;
import com.free.route.vo.ReqisterVo;
import com.free.route.vo.UserVo;
import com.free.zk.ClusterServerMonitor;
import com.free.zk.core.ClusterInfo;
import com.free.zk.core.ServerInfo;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/")
public class RouteController {

    private static final Logger logger = LoggerFactory.getLogger(RouteController.class);

    @Autowired
    private AccountService accountService;

    @Autowired
    private RouteService routeService;

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private OfflineStoreService offlineStoreService;

    @Autowired
    private ClusterServerMonitor clusterServerMonitor;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private MediaType mediaType = MediaType.parse("application/json");


    @RequestMapping("/reqister")
    public BaseResponse<UserVo> reqister(@RequestBody ReqisterVo reqisterVo) {

        BaseResponse<UserVo> res = new BaseResponse<>();
        UserVo info = accountService.reqister(reqisterVo);
        res.setDataBody(info);
        res.setCode(StatusEnum.SUCCESS.getCode());
        res.setMessage(StatusEnum.SUCCESS.getMessage());
        return res;
    }

    @RequestMapping("/register")
    public BaseResponse<UserVo> register(@RequestBody ReqisterVo reqisterVo) {
        return reqister(reqisterVo);
    }

    @RequestMapping("/login")
    public BaseResponse<MqttServerVo> login(@RequestBody LoginAo loginAo) {

        BaseResponse<MqttServerVo> res = new BaseResponse<>();
        StatusEnum status = accountService.login(new LoginReqVO(loginAo.getUserName(), loginAo.getToken()));

        if (status == StatusEnum.SUCCESS) {
            Long userId = TokenUtil.parseUserId(loginAo.getToken());
            MqttServerVo mqttServerVo = routeService.lbsServer(userId);

            if (mqttServerVo != null && mqttServerVo.getBrokerName() != null) {
                accountService.saveRouteInfo(userId, mqttServerVo.getBrokerName());
                res.setDataBody(mqttServerVo);
            } else {
                status = StatusEnum.FAIL;
                res.setMessage("No available MQTT brokers");
            }
        }

        res.setCode(status.getCode());
        res.setMessage(status.getMessage());
        return res;
    }

    @RequestMapping("/markBrokerDown")
    public BaseResponse<String> markBrokerDown(@RequestParam("brokerName") String brokerName) {
        routeService.markBrokerDown(brokerName);
        return BaseResponse.create(null, StatusEnum.SUCCESS);
    }

    @RequestMapping("/getBroker")
    public BaseResponse<MqttServerVo> getBroker(@RequestParam("userId") Long userId) {
        MqttServerVo broker = routeService.findUserBroker(userId);
        if (broker == null || broker.getIp() == null) {
            broker = routeService.lbsServer(userId);
            if (broker != null) {
                accountService.saveRouteInfo(userId, broker.getBrokerName());
            }
        }
        return BaseResponse.create(broker, StatusEnum.SUCCESS);
    }

    //服务器推送消息
    @RequestMapping("/pushMsg")
    public <T> BaseResponse<T> pushMsg(@RequestBody PushMsgAo<T> pushMsgAo) {

        BaseResponse rtv = BaseResponse.create(null, StatusEnum.SUCCESS);
        String msgUUID = pushMsgAo.getMsgUUID();
        if (msgUUID == null || msgUUID.trim().isEmpty()) {
            pushMsgAo.setMsgUUID(UUID.randomUUID().toString().replaceAll("-", ""));
        }

        try {
            MqttServerVo mqttServerVo = routeService.findUserBroker(pushMsgAo.getUserId());
            if (mqttServerVo == null || mqttServerVo.getIp() == null || mqttServerVo.getHttpPort() <= 0) {
                MqttServerVo candidate = routeService.lbsServer(pushMsgAo.getUserId());
                if (candidate != null && candidate.getIp() != null && candidate.getHttpPort() > 0) {
                    mqttServerVo = candidate;
                } else {
                    offlineStoreService.enqueue(pushMsgAo);
                    return rtv;
                }
            }

            boolean ok = tryPushToBroker(mqttServerVo, pushMsgAo);
            if (ok) {
                return rtv;
            }
        } catch (Exception e) {
            try {
                MqttServerVo candidate = routeService.lbsServer(pushMsgAo.getUserId());
                if (candidate != null && candidate.getIp() != null && candidate.getHttpPort() > 0) {
                    if (tryPushToBroker(candidate, pushMsgAo)) {
                        return rtv;
                    }
                }
            } catch (Exception ignored) {
            }
            offlineStoreService.enqueue(pushMsgAo);
        }

        return rtv;
    }

    private <T> boolean tryPushToBroker(MqttServerVo mqttServerVo, PushMsgAo<T> pushMsgAo) {
        try {
            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(mediaType, JSON.toJSONString(pushMsgAo));
            Request request = new Request.Builder()
                    .url("http://" + mqttServerVo.getIp() + ":" + mqttServerVo.getHttpPort() + "/pushMsg")
                    .post(requestBody)
                    .build();

            Response response = null;
            try {
                response = okHttpClient.newCall(request).execute();
                return response.isSuccessful();
            } finally {
                if (response != null && response.body() != null) {
                    response.body().close();
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    @RequestMapping("/offerLine")
    public <T> BaseResponse offerLine(@RequestBody UserAo userAo){
         accountService.offerLine(userAo.getUserId());
         return BaseResponse.success();
    }

    // ==================== Admin管理端API ====================

    /**
     * 获取所有已注册客户端列表（从Redis读取）
     * 包含：用户信息、在线状态、分配的Broker
     */
    @RequestMapping("/admin/clients")
    public BaseResponse<List<Map<String, Object>>> adminGetClients() {
        try {
            List<Map<String, Object>> clients = new ArrayList<>();

            // 扫描所有 user:status:* key
            Set<String> statusKeys = redisTemplate.keys("user:status:*");
            if (statusKeys != null && !statusKeys.isEmpty()) {
                for (String statusKey : statusKeys) {
                    try {
                        String userJson = redisTemplate.opsForValue().get(statusKey);
                        if (userJson == null) continue;

                        // 直接用JSONObject解析，确保能读到online字段
                        JSONObject jsonObj = JSON.parseObject(userJson);
                        Long userId = jsonObj.getLong("userId");
                        String userName = jsonObj.getString("userName");
                        if (userId == null) continue;

                        // 从 JSON 中读取 online 字段
                        boolean online = jsonObj.getBooleanValue("online");

                        Map<String, Object> client = new HashMap<>();
                        client.put("userId", userId);
                        client.put("userName", userName);
                        client.put("online", online);

                        // 从 user:broker:{userId} 读取分配的broker
                        String brokerName = redisTemplate.opsForValue().get("user:broker:" + userId);
                        client.put("brokerName", (brokerName != null && !brokerName.isEmpty()) ? brokerName : "");

                        // 在线时补充Broker连接信息
                        if (online) {
                            ClusterInfo clusterInfo = clusterServerMonitor.getClusterInfo();
                            if (clusterInfo != null && clusterInfo.getBrokerMap() != null) {
                                ServerInfo serverInfo = clusterInfo.getBrokerMap().get(brokerName);
                                if (serverInfo != null) {
                                    client.put("brokerIp", serverInfo.getIp());
                                    client.put("brokerTcpPort", serverInfo.getTcpPort());
                                    client.put("brokerHttpPort", serverInfo.getHttpPort());
                                }
                            }
                        }

                        clients.add(client);
                    } catch (Exception e) {
                        logger.warn("解析用户状态失败: key={}, err={}", statusKey, e.getMessage());
                    }
                }
            }

            return BaseResponse.success(clients);
        } catch (Exception e) {
            logger.error("获取客户端列表失败", e);
            return BaseResponse.fail("获取客户端列表失败: " + e.getMessage());
        }
    }

    /**
     * 清除所有用户数据（user:status:*, user:broker:*, user:*, user:inc, {userName}）
     * 然后重新创建测试用户，部分在线、部分离线
     */
    @RequestMapping("/admin/resetTestData")
    public BaseResponse<Map<String, Object>> adminResetTestData() {
        try {
            Map<String, Object> result = new HashMap<>();

            // 1. 清除所有旧用户数据
            Set<String> statusKeys = redisTemplate.keys("user:status:*");
            Set<String> brokerKeys = redisTemplate.keys("user:broker:*");
            Set<String> userKeys = redisTemplate.keys("user:*");

            int deletedCount = 0;
            if (statusKeys != null) { redisTemplate.delete(statusKeys); deletedCount += statusKeys.size(); }
            if (brokerKeys != null) { redisTemplate.delete(brokerKeys); deletedCount += brokerKeys.size(); }
            // user:* 包含 user:inc 和 user:{id} -> {userName} 的映射，一起清
            if (userKeys != null) { redisTemplate.delete(userKeys); deletedCount += userKeys.size(); }

            // 清除以用户名作为key的映射
            // 先收集所有可能的用户名key比较困难，用已知测试用户名清理
            String[] testNames = {"sensor-01", "sensor-02", "sensor-03", "device-01", "device-02",
                    "gateway-01", "gateway-02", "client-app-01", "client-app-02", "monitor-01"};
            for (String name : testNames) {
                redisTemplate.delete(name);
            }

            result.put("deletedKeys", deletedCount);

            // 2. 重置自增ID
            redisTemplate.opsForValue().set("user:inc", "0");

            // 3. 创建10个测试用户
            List<Map<String, Object>> createdUsers = new ArrayList<>();
            for (int i = 0; i < testNames.length; i++) {
                String userName = testNames[i];
                Long userId = redisTemplate.opsForValue().increment("user:inc", 1);
                String token = TokenUtil.generateToken(userId);

                // 前6个在线，后4个离线（在线状态由 online 字段决定）
                boolean isOnline = i < 6;
                UserVo userVo = new UserVo(userId, userName, token);
                userVo.setOnline(isOnline);
                String userJson = JSON.toJSONString(userVo);

                redisTemplate.opsForValue().set("user:" + userId, userName);
                redisTemplate.opsForValue().set(userName, "user:" + userId);
                redisTemplate.opsForValue().set("user:status:" + userId, userJson);

                Map<String, Object> u = new HashMap<>();
                u.put("userId", userId);
                u.put("userName", userName);
                u.put("token", token);
                u.put("online", isOnline);
                createdUsers.add(u);
            }
            result.put("createdUsers", createdUsers.size());

            // 4. 从ZK获取可用broker列表
            ClusterInfo clusterInfo = clusterServerMonitor.getClusterInfo();
            List<String> availableBrokers = new ArrayList<>();
            if (clusterInfo != null && clusterInfo.getBrokerMap() != null) {
                availableBrokers.addAll(clusterInfo.getBrokerMap().keySet());
            }

            // 5. 给在线用户分配broker路由
            int onlineCount = 0;
            for (int i = 0; i < createdUsers.size(); i++) {
                Map<String, Object> u = createdUsers.get(i);
                Long userId = ((Number) u.get("userId")).longValue();
                boolean isOnline = (Boolean) u.get("online");

                if (isOnline && !availableBrokers.isEmpty()) {
                    // 轮询分配broker
                    String brokerName = availableBrokers.get(i % availableBrokers.size());
                    redisTemplate.opsForValue().set("user:broker:" + userId, brokerName);
                    onlineCount++;
                }
            }
            result.put("onlineUsers", onlineCount);
            result.put("availableBrokers", availableBrokers);

            logger.info("测试数据重置完成: 删除{}个key, 创建{}个用户, {}个在线, 可用broker:{}",
                    deletedCount, createdUsers.size(), onlineCount, availableBrokers);

            return BaseResponse.success(result);
        } catch (Exception e) {
            logger.error("重置测试数据失败", e);
            return BaseResponse.fail("重置测试数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取集群Broker列表
     * 仅从 ClusterServerMonitor (ZK/Static) 获取真实在线的 broker
     */
    @RequestMapping("/admin/cluster")
    public BaseResponse<Map<String, Object>> adminGetCluster() {
        try {
            Map<String, Object> result = new HashMap<>();
            ClusterInfo clusterInfo = clusterServerMonitor.getClusterInfo();

            List<Map<String, Object>> brokerList = new ArrayList<>();
            if (clusterInfo != null) {
                // 合并 brokerMap 和 serverInfoList，brokerMap 优先
                Map<String, ServerInfo> allBrokers = new java.util.LinkedHashMap<>();
                if (clusterInfo.getBrokerMap() != null) {
                    allBrokers.putAll(clusterInfo.getBrokerMap());
                }
                if (clusterInfo.getServerInfoList() != null) {
                    for (ServerInfo info : clusterInfo.getServerInfoList()) {
                        if (!allBrokers.containsKey(info.getBrokerName())) {
                            allBrokers.put(info.getBrokerName(), info);
                        }
                    }
                }
                for (Map.Entry<String, ServerInfo> entry : allBrokers.entrySet()) {
                    ServerInfo info = entry.getValue();
                    Map<String, Object> broker = new HashMap<>();
                    broker.put("brokerName", info.getBrokerName());
                    broker.put("ip", info.getIp() != null ? info.getIp() : "");
                    broker.put("tcpPort", info.getTcpPort());
                    broker.put("httpPort", info.getHttpPort());
                    brokerList.add(broker);
                }
            }

            result.put("clusterName", clusterInfo != null ? clusterInfo.getClusterName() : "default");
            result.put("brokers", brokerList);
            result.put("brokerCount", brokerList.size());
            result.put("refreshTime", clusterInfo != null ? clusterInfo.getRefreshTime() : 0);

            return BaseResponse.success(result);
        } catch (Exception e) {
            logger.error("获取集群信息失败", e);
            return BaseResponse.fail("获取集群信息失败: " + e.getMessage());
        }
    }

}
