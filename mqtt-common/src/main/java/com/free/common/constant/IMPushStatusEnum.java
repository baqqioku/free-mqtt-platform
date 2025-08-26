package com.free.common.constant;

public enum IMPushStatusEnum {

    PUSHMSG_STATUS_MSG_UN_SEND(0, "消息未送达或已送到Client未给IM响应"),
    PUSHMSG_STATUS_MSG_SEND(1, "消息已送达并给服务端确认收到响应消息"),
    PUSHMSG_STATUS_MSG_EXPIRE(2, "消息过期无效");

    public final int code;
    public final String desc;

    IMPushStatusEnum(int code, String desc){
        this.code = code;
        this.desc = desc;
    }
}
