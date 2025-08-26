package com.free.route.ao;

import java.io.Serializable;

public class LoginAo implements Serializable {

    private String userName;

    private String token;

    public LoginAo() {
    }

    public LoginAo(String userName, String token) {
        this.userName = userName;
        this.token = token;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
