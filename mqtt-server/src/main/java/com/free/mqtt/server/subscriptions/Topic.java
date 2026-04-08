package com.free.mqtt.server.subscriptions;

public class Topic {
    
    private final String topicName;
    
    public Topic(String topicName) {
        this.topicName = topicName;
    }
    
    public String getTopicName() {
        return topicName;
    }
    
    public String getShareName() {
        return null;
    }
    
    public boolean isShared() {
        return false;
    }
    
    @Override
    public String toString() {
        return topicName;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Topic topic = (Topic) obj;
        return topicName != null ? topicName.equals(topic.topicName) : topic.topicName == null;
    }
    
    @Override
    public int hashCode() {
        return topicName != null ? topicName.hashCode() : 0;
    }
}
