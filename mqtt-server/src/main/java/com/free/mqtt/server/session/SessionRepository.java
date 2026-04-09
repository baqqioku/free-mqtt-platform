package com.free.mqtt.server.session;

import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.session.data.ClientSession;
import com.free.mqtt.server.subscriptions.ISubscriptionsDirectory;
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

    public static class OpenResult {
        private final ClientSession session;
        private final boolean sessionPresent;

        public OpenResult(ClientSession session, boolean sessionPresent) {
            this.session = session;
            this.sessionPresent = sessionPresent;
        }

        public ClientSession getSession() {
            return session;
        }

        public boolean isSessionPresent() {
            return sessionPresent;
        }
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

            if (clientSession.isCleanSession()) {
                sessionsCache.remove(clientId);
                clientSession.close();
            } else {
                clientSession.disconnectChannel(true);
            }
        }

        return true;
    }

    public OpenResult openOrCreateSession(MqttNettyChannel channel, String clientID, boolean cleanSession){
        ClientSession clientSession;
        synchronized (this) {
            clientSession = sessionsCache.get(clientID);
            if (cleanSession) {
                if (clientSession != null) {
                    clientSession.close();
                    sessionsCache.remove(clientID);
                }
                clientSession = new ClientSession(clientID, iSubscriptionsDirectory, channel);
                clientSession.setCleanSession(true);
                clientSession.setKeepAliveSeconds(5*60*1000);
                clientSession.setConnectTime(System.currentTimeMillis());
                sessionsCache.put(clientID, clientSession);
                return new OpenResult(clientSession, false);
            }

            if (clientSession != null) {
                clientSession.disconnectChannel(true);
                clientSession.setChannel(channel);
                clientSession.setCleanSession(false);
                clientSession.setConnectTime(System.currentTimeMillis());
                sessionsCache.put(clientID, clientSession);
                return new OpenResult(clientSession, true);
            }

            clientSession = new ClientSession(clientID, iSubscriptionsDirectory, channel);
            clientSession.setCleanSession(false);
            clientSession.setKeepAliveSeconds(5*60*1000);
            clientSession.setConnectTime(System.currentTimeMillis());
            sessionsCache.put(clientID, clientSession);
            return new OpenResult(clientSession, false);
        }
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
