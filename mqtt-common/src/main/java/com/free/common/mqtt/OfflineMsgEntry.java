package com.free.common.mqtt;

public class OfflineMsgEntry {
    public long userId;
    public String topic;
    public String payloadBase64;
    public int qos;
    public Integer businessMsgId;
    public long createTime;
    public long ttl;
    public long expireAt;
    public String msgUUID;
}
