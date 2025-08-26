package com.free.mqtt.server.subscriptions;

import com.free.mqtt.server.subscriptions.data.Topic;

public class TopicConstant {
	public final static String EMPTY = "";
	
	public final static String MULTI = "#";
	
	public final static String SINGLE = "+";
	
	public final static String SPLIT = "/";
	
	public final static Topic SYS_MONITOR_TOPIC = new Topic("/sys/monitor");
}
