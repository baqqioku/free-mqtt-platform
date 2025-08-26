package com.free.vo;

import java.io.Serializable;

public class ReqisterVo implements Serializable {


    private String userName;
    private String passWord;

    public ReqisterVo( String userName, String passWord) {
        this.userName = userName;
        this.passWord = passWord;
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
