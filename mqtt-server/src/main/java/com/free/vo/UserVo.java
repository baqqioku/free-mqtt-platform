package com.free.vo;

import java.io.Serializable;

public class UserVo implements Serializable {

    private Long userId ;
    private String userName ;
    private String passWord;


    public UserVo(Long userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    public UserVo(Long userId, String userName, String passWord) {
        this.userId = userId;
        this.userName = userName;
        this.passWord = passWord;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassWord() {
        return passWord;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }
}
