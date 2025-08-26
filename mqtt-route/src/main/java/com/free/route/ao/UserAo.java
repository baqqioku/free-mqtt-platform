package com.free.route.ao;

import java.io.Serializable;

public class UserAo implements Serializable {

    private Long userId;

    public UserAo() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
