package com.free.mqtt.server.session;

import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.session.data.ClientSession;
import com.free.mqtt.server.subscriptions.ISubscriptionsDirectory;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRepository {

    private static final Logger logger = LoggerFactory.getLogger(SessionRepository.class);

    private Map<String, ClientSession> sessionsCache = new ConcurrentHashMap<>();

    private ISubscriptionsDirectory iSubscriptionsDirectory;

    public SessionRepository(ISubscriptionsDirectory iSubscriptionsDirectory) {
        this.iSubscriptionsDirectory = iSubscriptionsDirectory;
    }

    public boolean removeClinetSession(MqttNettyChannel channel, String clientId){
        ClientSession clientSession = sessionsCache.get(clientId);
        if(null == clientSession){
            return false;
        }

        synchronized (this) {
            if( !clientSession.isSameChannel(channel) ){
                return false;
            }

            sessionsCache.remove(clientId);
            clientSession.close();
        }

        return true;
    };

    public ClientSession createClientSession(MqttNettyChannel channel, String clientID){
//        logger.info("create get keys:{},clientId:{}", sessionsCache.keySet(), clientID);
        ClientSession clientSession = null;
        synchronized (this) {
            clientSession = sessionsCache.get(clientID);
            if(null != clientSession){
                clientSession.close();
            }

            clientSession = new ClientSession(clientID, iSubscriptionsDirectory, channel);
            clientSession.setKeepAliveSeconds(5*60*1000);//session的保存时间
            clientSession.setConnectTime(System.currentTimeMillis());
            sessionsCache.put(clientID,clientSession);
        }

        return clientSession;
    }

    public void putClinetSession(String clientId,ClientSession clientSession){
        sessionsCache.put(clientId, clientSession);
    }

    public ClientSession getSession(String clientID) {
        return sessionsCache.get(clientID);
    }

    public Map<String, ClientSession> getSessionsCache() {
        return sessionsCache;
    }

    public void setSessionsCache(Map<String, ClientSession> sessionsCache) {
        this.sessionsCache = sessionsCache;
    }

    public ISubscriptionsDirectory getiSubscriptionsDirectory() {
        return iSubscriptionsDirectory;
    }

    public void setiSubscriptionsDirectory(ISubscriptionsDirectory iSubscriptionsDirectory) {
        this.iSubscriptionsDirectory = iSubscriptionsDirectory;
    }
}
