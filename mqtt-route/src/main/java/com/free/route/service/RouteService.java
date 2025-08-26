package com.free.route.service;

import com.free.route.vo.MqttServerVo;

public interface RouteService {

    public MqttServerVo lbsServer(Long userId);

    public MqttServerVo findUserBroker(Long userId);
}
