package com.free.mqtt.server.session.data;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.io.Serializable;
import java.util.Arrays;

public class StoredMessage implements Serializable{
    private static final long serialVersionUID = 1755296138639817304L;
    
    private MqttQoS qos;
    
    private byte[] payload;
    
    private String topic;
    
    private boolean retained;
    
    private String clientID;
    
    private Integer businessMsgId;
    
    private long ttl;
    
    private long createTime;

    private long sendTime;

    private String msgUUID;

    public StoredMessage(byte[] message, MqttQoS qos, String topic) {
    	this.payload = message;
        this.qos = qos;
        this.topic = topic;
        this.createTime = System.currentTimeMillis();
    }

    public StoredMessage(byte[] message, MqttQoS qos, String topic, String clientID, Integer businessMsgId, long ttl, String msgUUID) {
    	this.payload = message;
        this.qos = qos;
        this.topic = topic;
        this.clientID = clientID;
        this.businessMsgId = businessMsgId;
        this.createTime = System.currentTimeMillis();;
        this.ttl = ttl;
        this.msgUUID = msgUUID;
    }

    public void checkArg(){
		if( null == getTopic() ){
			throw new RuntimeException("构造的数据不合法   topic");
		}

		if(null == getPayload()){
			throw new RuntimeException("构造的数据不合法   Payload");
		}

		if( null == getQos() ){
			throw new RuntimeException("构造的数据不合法   Qos");
		}

		if( null == getClientID() ){
			throw new RuntimeException("构造的数据不合法   ClientID");
		}
    }

	public MqttQoS getQos() {
		return qos;
	}

	public void setQos(MqttQoS qos) {
		this.qos = qos;
	}

	public byte[] getPayload() {
		return payload;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public boolean isRetained() {
		return retained;
	}

	public void setRetained(boolean retained) {
		this.retained = retained;
	}

	public String getClientID() {
		return clientID;
	}

	public void setClientID(String clientID) {
		this.clientID = clientID;
	}


	public Integer getBusinessMsgId() {
		return businessMsgId;
	}

	public void setBusinessMsgId(Integer businessMsgId) {
		this.businessMsgId = businessMsgId;
	}

	public long getCreateTime() {
		return createTime;
	}

	public void setCreateTime(long createTime) {
		this.createTime = createTime;
	}

	public long getSendTime() {
		return sendTime;
	}

	public void setSendTime(long sendTime) {
		this.sendTime = sendTime;
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

	@Override
	public String toString() {
		return "StoredMessage [qos=" + qos + ", payload=" + Arrays.toString(payload) + ", topic=" + topic
				+ ", retained=" + retained + ", clientID=" + clientID + ", pushMessageId="
				+ businessMsgId + ", msgUUID=" + msgUUID + "]";
	}
    

}
