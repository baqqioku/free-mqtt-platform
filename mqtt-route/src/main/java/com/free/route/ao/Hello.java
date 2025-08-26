package com.free.route.ao;

import java.io.Serializable;

public class Hello implements Serializable {

    private String msg;

    private UserAo userAo;

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public UserAo getUserAo() {
        return userAo;
    }

    public void setUserAo(UserAo userAo) {
        this.userAo = userAo;
    }
}
