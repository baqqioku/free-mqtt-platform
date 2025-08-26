package com.free.mqtt.server.listener.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.free.common.constant.MqttConstant;
import com.free.mqtt.MqttServer;
import com.free.mqtt.server.listener.MqttMsgListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.free.common.constant.RedisKeyConstant.USER_STATUS;


/**
 * http 消息转mqtt 消息回调接口，异步处理
 */
public class Mqtt2HttpMsgListener implements MqttMsgListener {

    private static final Logger logger = LoggerFactory.getLogger(Mqtt2HttpMsgListener.class);

    private RedisTemplate<String,String> redisTemplate;

    private MqttServer mqttServer;

    private Map<String, Long> disConnectTimeMap = new ConcurrentHashMap<String, Long>();
    private Map<String, Long> clientIdToUserIdMap = new ConcurrentHashMap<String, Long>();

    public Mqtt2HttpMsgListener(RedisTemplate<String, String> redisTemplate, MqttServer mqttServer) {
        this.redisTemplate = redisTemplate;
        this.mqttServer = mqttServer;
    }

    @Override
    public boolean mqttChannelAuth(String clientId, String userName, String password) {
        boolean authSuccess = false;
        try {
            String userId = clientId.split("_")[1];
            String userJson = redisTemplate.opsForValue().get(USER_STATUS + userId);
            JSONObject jsonObject = JSON.parseObject(userJson);
            if (userName.equals(jsonObject.getString("userName")) && password.equals(jsonObject.getString("token"))) {
                authSuccess =  true;
                addClientInfo(clientId,Long.valueOf(userId));
            }
        }catch (Exception e){
            logger.error("mqtt授权失败，请求失败 clientId:{},userName:{},password:{}", clientId, userName, password, e);
            authSuccess  = false;
        }
        return authSuccess;
    }

    @Override
    public String autoSub(String clientId) {
        Long userId = clientIdToUserIdMap.get(clientId);
        if(userId == null){
            return null;
        }
        String autoSubTopic = MqttConstant.brokerToClientTopic+userId;
        return autoSubTopic;
    }

    /*单线程调用*/
    public void addClientInfo(String clientId, Long userId) {
        synchronized (this) {
            disConnectTimeMap.remove(clientId);
            clientIdToUserIdMap.put(clientId, userId);
        }
    }

}
