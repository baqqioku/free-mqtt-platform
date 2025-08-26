package com.free.route.service;


import com.free.common.constant.StatusEnum;
import com.free.route.vo.LoginReqVO;
import com.free.route.vo.ReqisterVo;
import com.free.route.vo.UserVo;

public interface AccountService {

    public UserVo reqister(ReqisterVo reqisterVo);

    public StatusEnum login(LoginReqVO loginReqVO);

    public void saveRouteInfo(Long userId, String brokerName);

    public void offerLine(Long userId);





    }
