package com.free.common.utils;

public class TopicUtil {

	public static Long getIdFromTopic(String topic){
		String[] tokens = topic.split("/");
		
		return Long.valueOf(tokens[tokens.length - 1]);
	}
	
	public static String getLastTokenFromTopic(String topic){
		String[] tokens = topic.split("/");
		
		return tokens[tokens.length - 1];
	}
	
}
