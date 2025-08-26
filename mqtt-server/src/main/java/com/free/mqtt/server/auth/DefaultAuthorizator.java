package com.free.mqtt.server.auth;

import com.free.mqtt.server.subscriptions.data.Topic;

public class DefaultAuthorizator implements IAuthorizator {
	
	public DefaultAuthorizator(){
	}

	@Override
	public boolean canWrite(Topic topic, String user, String client) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean canRead(Topic topic, String user, String client) {
		// TODO Auto-generated method stub
		return true;
	}

}
