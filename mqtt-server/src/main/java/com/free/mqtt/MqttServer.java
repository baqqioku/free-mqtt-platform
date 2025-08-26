package com.free.mqtt;


import com.free.ao.PushMsgAo;
import com.free.common.constant.MqttConstant;
import com.free.mqtt.server.MqttMsgProcessThread;
import com.free.mqtt.server.MqttPushRequest;
import com.free.mqtt.server.config.HttpServerConfig;
import com.free.mqtt.server.config.MqttConfig;
import com.free.mqtt.server.config.MqttServerConfig;
import com.free.mqtt.server.interceptor.Interceptor;
import com.free.mqtt.server.interceptor.MqttInterceptor;
import com.free.mqtt.server.listener.MqttMsgListener;
import com.free.mqtt.server.listener.impl.Mqtt2HttpMsgListener;
import com.free.mqtt.server.netty.AbstractMqttServer;
import com.free.mqtt.server.netty.MqttNettyServer;
import com.free.mqtt.server.netty.handler.MqttProcessHandler;
import com.free.mqtt.server.qos.ProtocolProcessor;
import com.free.mqtt.server.session.SessionRepository;
import com.free.mqtt.server.subscriptions.ISubscriptionsDirectory;
import com.free.mqtt.server.subscriptions.TreeSubscriptionDirectory;
import com.free.mqtt.server.utils.PerfUtil;
import com.free.zk.ClusterServerMonitor;
import com.free.zk.core.ServerInfo;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;


@Component
public class MqttServer {

    private static Logger logger = LoggerFactory.getLogger(MqttServer.class);

    @Autowired
    private MqttConfig mqttConfig;

    @Autowired
    private ClusterServerMonitor clusterServerMonitor;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    private MqttServerConfig mqttServerConfig;

    @Autowired
    private HttpServerConfig httpServerConfig;

    private AbstractMqttServer nettyServer;




    private ProtocolProcessor protocolProcessor ;
    private ISubscriptionsDirectory subscriptionsDirectory;
    private SessionRepository sessionRepository ;

    private MqttMsgListener mqttMsgListener;
    private MqttMsgProcessThread mqttMsgProcessThread;




    public void start() {
        PerfUtil.init(mqttConfig);
        initServer();
    }

    private void initServer() {

        try {
            Interceptor interceptor = new MqttInterceptor(this,mqttServerConfig.getFilterTopic());
            subscriptionsDirectory = new TreeSubscriptionDirectory();
            sessionRepository = new SessionRepository(subscriptionsDirectory);

            protocolProcessor = new ProtocolProcessor(sessionRepository, interceptor, subscriptionsDirectory);
            MqttProcessHandler mqttProcessHandler = new MqttProcessHandler(protocolProcessor);

            mqttMsgListener  = new Mqtt2HttpMsgListener(redisTemplate,this);
            mqttMsgProcessThread = new MqttMsgProcessThread(this,50);

            nettyServer = new MqttNettyServer(mqttServerConfig, mqttProcessHandler);

            ServerInfo serverInfo = clusterServerMonitor.initServerInfo(mqttServerConfig.getTcpPort(),httpServerConfig.getHttpPort());
            clusterServerMonitor.register(serverInfo);
            clusterServerMonitor.monitor();
        } catch (Exception e) {
            logger.error("启动IM消息中心服务异常", e);

            throw new RuntimeException("启动IM消息中心服务异常");
        }
    }

    public void stop() {
        nettyServer.stop();
    }


    public void sendMsg(MqttPushRequest request){
        try{
            //String clientId = request.getClientId();
            String topic = request.getTargetTopic();
            byte[] payload = request.getJsonData().getBytes("utf-8");
            MqttQoS qos = MqttQoS.AT_LEAST_ONCE;
            Integer businessMsgId = request.getBusinessMsgIdId();
            long createTime = request.getRequestProcEndTime();
            long ttl = request.getTtl();

            protocolProcessor.sendMsg(topic, payload, qos, businessMsgId, createTime, ttl, request.getMsgUUID());
        }catch(Exception e){

        }
    }

    public ProtocolProcessor getProtocolProcessor() {
        return protocolProcessor;
    }

    public void setProtocolProcessor(ProtocolProcessor protocolProcessor) {
        this.protocolProcessor = protocolProcessor;
    }

    public ISubscriptionsDirectory getSubscriptionsDirectory() {
        return subscriptionsDirectory;
    }

    public void setSubscriptionsDirectory(ISubscriptionsDirectory subscriptionsDirectory) {
        this.subscriptionsDirectory = subscriptionsDirectory;
    }

    public SessionRepository getSessionRepository() {
        return sessionRepository;
    }

    public void setSessionRepository(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public MqttMsgListener getMqttMsgListener() {
        return mqttMsgListener;
    }

    public void setMqttMsgListener(MqttMsgListener mqttMsgListener) {
        this.mqttMsgListener = mqttMsgListener;
    }

    public MqttMsgProcessThread getMqttMsgProcessThread() {
        return mqttMsgProcessThread;
    }

    public void setMqttMsgProcessThread(MqttMsgProcessThread mqttMsgProcessThread) {
        this.mqttMsgProcessThread = mqttMsgProcessThread;
    }
}
