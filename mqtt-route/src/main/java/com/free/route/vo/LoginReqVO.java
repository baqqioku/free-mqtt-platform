package com.free.route.vo;

public class LoginReqVO {

    private String userName;

    private String passWord;

    public LoginReqVO(String userName, String passWord) {
        this.userName = userName;
        this.passWord = passWord;
    }

    public String getPassWord() {
        return passWord;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String toString() {
        return "LoginReqVO{" +
                "userName='" + userName + '\'' +
                ", passWord='" + passWord + '\'' +
                '}';
    }
}
