package com.free.mqtt.server.handler.eventHandler;

import com.free.mqtt.MqttServer;
import com.free.mqtt.server.concurrent.AsyncThreadPoolExecutor;
import com.free.mqtt.server.concurrent.CallbackFutureTask;
import com.free.mqtt.server.concurrent.FutureCallBack;
import com.free.mqtt.server.auth.AuthChannel;
import com.free.mqtt.server.event.MqttAuthEvent;
import com.free.mqtt.server.handler.MqttBaseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class MqttAuthHandler extends MqttBaseHandler<MqttAuthEvent> {

    private static Logger logger = LoggerFactory.getLogger(MqttAuthHandler.class);

    public MqttAuthHandler(AsyncThreadPoolExecutor asyncThreadPoolExcecutor, MqttServer mqttServer) {
        super(asyncThreadPoolExcecutor, mqttServer);
    }

    @Override
    public void handle(MqttAuthEvent event) {
        AuthChannel authChannel = event.getAuthChannel();


        asyncThreadPoolExcecutor.submit(new CallbackFutureTask(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return mqttServer.getMqttMsgListener().mqttChannelAuth(event.getClientId(), authChannel.getUserName(), authChannel.getPassWord());
            }
        }, new FutureCallBack<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if(result){
                    authChannel.authSuccess();
                }else {
                    this.onFailure(result);
                }

            }

            @Override
            public void onFailure(Boolean result) {
                authChannel.authFail();
                logger.info("授权失败,ClientId:{}",event.getClientId());

            }

            @Override
            public void onError(Throwable t) {
                logger.info("授权失败,ClientId:{}",event.getClientId(),t);
            }
        }));
    }
}
