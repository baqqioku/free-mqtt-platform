package com.free.route.web;


import com.alibaba.fastjson.JSON;
import com.free.common.constant.MqttConstant;
import com.free.common.constant.StatusEnum;
import com.free.common.resp.BaseResponse;
import com.free.common.utils.TokenUtil;
import com.free.route.ao.LoginAo;
import com.free.route.ao.PushMsgAo;
import com.free.route.ao.UserAo;
import com.free.route.service.AccountService;
import com.free.route.service.OfflineStoreService;
import com.free.route.service.RouteService;
import com.free.route.vo.LoginReqVO;
import com.free.route.vo.MqttServerVo;
import com.free.route.vo.ReqisterVo;
import com.free.route.vo.UserVo;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/")
public class RouteController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private RouteService routeService;

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private OfflineStoreService offlineStoreService;

    private MediaType mediaType = MediaType.parse("application/json");


    @RequestMapping("/reqister")
    public BaseResponse<UserVo> reqister(@RequestBody ReqisterVo reqisterVo) {

        BaseResponse<UserVo> res = new BaseResponse<>();
        UserVo info = accountService.reqister(reqisterVo);
        res.setDataBody(info);
        res.setCode(StatusEnum.SUCCESS.getCode());
        res.setMessage(StatusEnum.SUCCESS.getMessage());
        return res;
    }

    @RequestMapping("/register")
    public BaseResponse<UserVo> register(@RequestBody ReqisterVo reqisterVo) {
        return reqister(reqisterVo);
    }

    @RequestMapping("/login")
    public BaseResponse<MqttServerVo> login(@RequestBody LoginAo loginAo) {

        BaseResponse<MqttServerVo> res = new BaseResponse<>();
        StatusEnum status = accountService.login(new LoginReqVO(loginAo.getUserName(), loginAo.getToken()));

        if (status == StatusEnum.SUCCESS) {
            Long userId = TokenUtil.parseUserId(loginAo.getToken());
            MqttServerVo mqttServerVo = routeService.lbsServer(userId);

            if (mqttServerVo != null && mqttServerVo.getBrokerName() != null) {
                accountService.saveRouteInfo(userId, mqttServerVo.getBrokerName());
                res.setDataBody(mqttServerVo);
            } else {
                status = StatusEnum.FAIL;
                res.setMessage("No available MQTT brokers");
            }
        }

        res.setCode(status.getCode());
        res.setMessage(status.getMessage());
        return res;
    }

    @RequestMapping("/markBrokerDown")
    public BaseResponse<String> markBrokerDown(@RequestParam("brokerName") String brokerName) {
        routeService.markBrokerDown(brokerName);
        return BaseResponse.create(null, StatusEnum.SUCCESS);
    }

    @RequestMapping("/getBroker")
    public BaseResponse<MqttServerVo> getBroker(@RequestParam("userId") Long userId) {
        MqttServerVo broker = routeService.findUserBroker(userId);
        if (broker == null || broker.getIp() == null) {
            broker = routeService.lbsServer(userId);
            if (broker != null) {
                accountService.saveRouteInfo(userId, broker.getBrokerName());
            }
        }
        return BaseResponse.create(broker, StatusEnum.SUCCESS);
    }

    //服务器推送消息
    @RequestMapping("/pushMsg")
    public <T> BaseResponse<T> pushMsg(@RequestBody PushMsgAo<T> pushMsgAo) {

        BaseResponse rtv = BaseResponse.create(null, StatusEnum.SUCCESS);
        String msgUUID = pushMsgAo.getMsgUUID();
        if (msgUUID == null || msgUUID.trim().isEmpty()) {
            pushMsgAo.setMsgUUID(UUID.randomUUID().toString().replaceAll("-", ""));
        }

        try {
            MqttServerVo mqttServerVo = routeService.findUserBroker(pushMsgAo.getUserId());
            if (mqttServerVo == null || mqttServerVo.getIp() == null || mqttServerVo.getHttpPort() <= 0) {
                MqttServerVo candidate = routeService.lbsServer(pushMsgAo.getUserId());
                if (candidate != null && candidate.getIp() != null && candidate.getHttpPort() > 0) {
                    mqttServerVo = candidate;
                } else {
                    offlineStoreService.enqueue(pushMsgAo);
                    return rtv;
                }
            }

            boolean ok = tryPushToBroker(mqttServerVo, pushMsgAo);
            if (ok) {
                return rtv;
            }
        } catch (Exception e) {
            try {
                MqttServerVo candidate = routeService.lbsServer(pushMsgAo.getUserId());
                if (candidate != null && candidate.getIp() != null && candidate.getHttpPort() > 0) {
                    if (tryPushToBroker(candidate, pushMsgAo)) {
                        return rtv;
                    }
                }
            } catch (Exception ignored) {
            }
            offlineStoreService.enqueue(pushMsgAo);
        }

        return rtv;
    }

    private <T> boolean tryPushToBroker(MqttServerVo mqttServerVo, PushMsgAo<T> pushMsgAo) {
        try {
            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(mediaType, JSON.toJSONString(pushMsgAo));
            Request request = new Request.Builder()
                    .url("http://" + mqttServerVo.getIp() + ":" + mqttServerVo.getHttpPort() + "/pushMsg")
                    .post(requestBody)
                    .build();

            Response response = null;
            try {
                response = okHttpClient.newCall(request).execute();
                return response.isSuccessful();
            } finally {
                if (response != null && response.body() != null) {
                    response.body().close();
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    @RequestMapping("/offerLine")
    public <T> BaseResponse offerLine(@RequestBody UserAo userAo){
         accountService.offerLine(userAo.getUserId());
         return BaseResponse.success();
    }

}
