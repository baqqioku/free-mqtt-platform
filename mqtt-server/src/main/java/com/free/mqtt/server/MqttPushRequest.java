package com.free.mqtt.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 封装的app的mqtt与http请求
 *
 *
 */
public class MqttPushRequest {
    public static Logger logger = LoggerFactory.getLogger(MqttPushRequest.class);

    /** mqtt返回目标信息的topic **/
    private String targetTopic;

    /** 推送消息至客户端的消息id **/
    private Integer businessMsgId;

    /** mqtt clientId **/
    //private String clientId;

    private long requestProcEndTime = System.currentTimeMillis();
    private long ttl;
    private String jsonData;

    private String msgUUID;

    public MqttPushRequest(/*String clientId,*/ String targetTopic, String jsonData, String msgUUID) {
        this.targetTopic = targetTopic;
        //this.clientId = clientId;
        this.jsonData = jsonData;
        this.msgUUID = msgUUID;
    }

    public MqttPushRequest(String targetTopic, Integer businessMsgId, long ttl, String jsonData, String msgUUID) {
        this.targetTopic = targetTopic;
        this.businessMsgId = businessMsgId;
        this.ttl = ttl;
        this.jsonData = jsonData;
        this.msgUUID = msgUUID;
    }

    public String getTargetTopic() {
        return targetTopic;
    }

    public void setTargetTopic(String targetTopic) {
        this.targetTopic = targetTopic;
    }

    public String getJsonData() {
        return jsonData;
    }

    public Integer getBusinessMsgIdId() {
        return businessMsgId;
    }

    public void setBusinessMsgId(Integer businessMsgId) {
        this.businessMsgId = businessMsgId;
    }

/*    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }*/

    public long getRequestProcEndTime() {
        return requestProcEndTime;
    }

    public void setRequestProcEndTime(long requestProcEndTime) {
        this.requestProcEndTime = requestProcEndTime;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public String getMsgUUID() {
        return msgUUID;
    }

    public void setMsgUUID(String msgUUID) {
        this.msgUUID = msgUUID;
    }
}
