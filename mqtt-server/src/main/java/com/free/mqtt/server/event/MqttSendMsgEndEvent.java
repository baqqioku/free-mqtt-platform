package com.free.mqtt.server.event;

/**
 * 消息已经推送到客户端
 * @author Administrator
 *
 */
public class MqttSendMsgEndEvent extends MqttBaseEvent{
	private String topic;
	
	private Integer pushMessageId;
	
	public MqttSendMsgEndEvent(String clientId, String topic, Integer pushMessageId){
		super(clientId);
		
		this.topic = topic;
		this.pushMessageId = pushMessageId;
	}


	public String getTopic() {
		return topic;
	}

	public Integer getPushMessageId() {
		return pushMessageId;
	}


}
