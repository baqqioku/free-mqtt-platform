package com.free.route.web;


import com.alibaba.fastjson.JSON;
import com.free.common.constant.StatusEnum;
import com.free.common.resp.BaseResponse;
import com.free.common.utils.TokenUtil;
import com.free.route.ao.LoginAo;
import com.free.route.ao.PushMsgAo;
import com.free.route.ao.UserAo;
import com.free.route.service.AccountService;
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

    @RequestMapping("/login")
    public BaseResponse<MqttServerVo> login(@RequestBody LoginAo loginAo) {

        BaseResponse<MqttServerVo> res = new BaseResponse<>();
        StatusEnum status = accountService.login(new LoginReqVO(loginAo.getUserName(), loginAo.getToken()));

        if (status == StatusEnum.SUCCESS) {
            Long userId = TokenUtil.parseUserId(loginAo.getToken());
            MqttServerVo mqttServerVo = routeService.lbsServer(userId);

            accountService.saveRouteInfo(userId,mqttServerVo.getBrokerName());
            res.setDataBody(mqttServerVo);
        }

        res.setCode(status.getCode());
        res.setMessage(status.getMessage());
        return res;
    }

    //服务器推送消息
    @RequestMapping("/pushMsg")
    public <T> BaseResponse<T> pushMsg(@RequestBody PushMsgAo<T> pushMsgAo) {

        BaseResponse rtv = BaseResponse.success();
        MqttServerVo mqttServerVo = routeService.findUserBroker(pushMsgAo.getUserId());

        if(mqttServerVo == null){
            return BaseResponse.error("服务器获取失败");
        }

        pushMsgAo.setMsgUUID(UUID.randomUUID().toString().replaceAll("-", ""));

        okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(mediaType, JSON.toJSONString(pushMsgAo));

        Request request = new Request.Builder()
                .url("http://"+mqttServerVo.getIp()+":"+mqttServerVo.getHttpPort()+"/pushMsg")
                .post(requestBody)
                .build();
        Response response = null;
        try {
             response = okHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                rtv= BaseResponse.error("服务器获取失败");
            }
        } catch (Exception e) {
            rtv= BaseResponse.error("服务器获取失败");
        } finally {
            response.body().close();
        }

        return rtv;
    }

    @RequestMapping("/offerLine")
    public <T> BaseResponse offerLine(@RequestBody UserAo userAo){
        return null;
    }

}
