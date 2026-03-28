package com.free.mqtt.server.event;

public class MqttDisconnectEvent extends MqttBaseEvent{

	public MqttDisconnectEvent(String clientId) {
		super(clientId);
	}

}
