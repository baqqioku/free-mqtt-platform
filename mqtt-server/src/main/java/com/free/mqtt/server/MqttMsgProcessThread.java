package com.free.mqtt.server;

import com.free.mqtt.MqttServer;
import com.free.mqtt.server.concurrent.AsyncThreadPoolExecutor;
import com.free.mqtt.server.event.MqttAuthEvent;
import com.free.mqtt.server.event.MqttBaseEvent;
import com.free.mqtt.server.handler.eventHandler.MqttAuthHandler;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MqttMsgProcessThread extends BaseThread<MqttBaseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(MqttMsgProcessThread.class);

    private Map<Class<?>, Handler> handlers = new HashMap<>();

    private MqttServer mqttServer;

    private AsyncThreadPoolExecutor asyncThreadPoolExecutor;

    public MqttMsgProcessThread(MqttServer mqttServer,int threadNum){
        this.mqttServer = mqttServer;
        if (threadNum > 0){
            //默认cpu核心数目的两倍
            asyncThreadPoolExecutor = new AsyncThreadPoolExecutor(threadNum);
        } else {
            //默认cpu核心数目的两倍
            asyncThreadPoolExecutor = new AsyncThreadPoolExecutor(0);
        }

        initHandler();
        start();
    }

    private void initHandler(){
        handlers.put(MqttAuthEvent.class, new MqttAuthHandler(asyncThreadPoolExecutor, mqttServer));
    }



    @Override
    public void doing(MqttBaseEvent event) {
        if(null == event){
            return;
        }

        try{
            Handler handler = handlers.get(event.getClass());
            if (null == handler) {
                logger.error("不支持的事件类型 eventClass:{}", event.getClass().getName());
                return;
            }

            handler.handle(event);
        }catch(Exception e){
            logger.error("处理事件消息异常",e);
        }
    }
}
