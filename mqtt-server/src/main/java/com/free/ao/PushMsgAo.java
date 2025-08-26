package com.free.ao;

import com.free.common.constant.IMPushStatusEnum;

import java.io.Serializable;

import static com.free.common.constant.MqttConstant.MSG_TTL;


public class PushMsgAo<T> implements Serializable {

    private long userId;

    private int messageId;

    private long ttl = MSG_TTL;// 以秒为单位

	//json 报文
    private T data;

    private String url;

    private String extenData;
    
    private long createTime = System.currentTimeMillis();

    private long updateTime;//响应成功时间点，确认收到Client端的反馈并记录这个时间点

    private int status = IMPushStatusEnum.PUSHMSG_STATUS_MSG_UN_SEND.code;

    private String msgUUID;

	public long getUserId() {
		return userId;
	}

	public void setUserId(long userId) {
		this.userId = userId;
	}

	public int getMessageId() {
		return messageId;
	}

	public void setMessageId(int messageId) {
		this.messageId = messageId;
	}

	public long getTtl() {
		return ttl;
	}

	public void setTtl(long ttl) {
		this.ttl = ttl;
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getExtenData() {
		return extenData;
	}

	public void setExtenData(String extenData) {
		this.extenData = extenData;
	}

	public long getCreateTime() {
		return createTime;
	}

	public void setCreateTime(long createTime) {
		this.createTime = createTime;
	}

	public long getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(long updateTime) {
		this.updateTime = updateTime;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getMsgUUID() {
		return msgUUID;
	}

	public void setMsgUUID(String msgUUID) {
		this.msgUUID = msgUUID;
	}




}
