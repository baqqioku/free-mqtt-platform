package com.free.common.req;

import java.io.Serializable;

public class BaseRequest implements Serializable {

    private long createTime = System.currentTimeMillis();

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
