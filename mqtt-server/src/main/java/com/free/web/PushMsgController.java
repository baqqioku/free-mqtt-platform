package com.free.web;


import com.alibaba.fastjson.JSON;
import com.free.ao.PushMsgAo;
import com.free.common.constant.MqttConstant;
import com.free.common.resp.BaseResponse;
import com.free.mqtt.MqttServer;
import com.free.mqtt.server.MqttPushRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class PushMsgController {


    @Autowired
    private MqttServer mqttServer;

    //服务器推送消息
    @RequestMapping("/pushMsg")
    public <T> BaseResponse<T> pushMsg(@RequestBody PushMsgAo<T> pushMsgAo) {

        BaseResponse rtv = BaseResponse.success();

        String targetTopic = MqttConstant.brokerToClientTopic+pushMsgAo.getUserId();

        MqttPushRequest mqttPushRequest = new MqttPushRequest(targetTopic, pushMsgAo.getMessageId(),pushMsgAo.getTtl(),JSON.toJSONString(pushMsgAo), pushMsgAo.getMsgUUID());

        mqttServer.sendMsg(mqttPushRequest);
        return rtv;
    }

}
