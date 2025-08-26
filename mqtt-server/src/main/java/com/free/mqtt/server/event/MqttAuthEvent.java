package com.free.mqtt.server.event;


import com.free.mqtt.server.auth.AuthChannel;

public class MqttAuthEvent extends MqttBaseEvent {

	private AuthChannel authChannel;
	
	public MqttAuthEvent(String clientId, AuthChannel authChannel) {
		super(clientId);
		
		this.authChannel = authChannel;
	}

	public AuthChannel getAuthChannel() {
		return authChannel;
	}

}
