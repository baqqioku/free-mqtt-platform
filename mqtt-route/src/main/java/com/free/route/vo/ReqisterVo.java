package com.free.route.vo;

import java.io.Serializable;

public class ReqisterVo implements Serializable {


    private String userName;

    public ReqisterVo() {
    }

    public ReqisterVo(String userName) {
        this.userName = userName;
    }


    public ReqisterVo(String userName, String passWord) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }


}
