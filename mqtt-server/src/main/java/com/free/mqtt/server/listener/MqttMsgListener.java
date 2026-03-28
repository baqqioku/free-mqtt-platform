package com.free.mqtt.server.listener;

import com.free.mqtt.server.event.MqttSendMsgEndEvent;
import com.free.mqtt.server.session.data.StoredMessage;

import java.util.List;

public interface MqttMsgListener {


    boolean mqttChannelAuth(String clientId, String userName, String password);

    void notifySendMsgOk(MqttSendMsgEndEvent pushMsgEndInfo);

    void notifyDisconnect(String clientId);

    String autoSub(String clientId);

    void storeOfflineMessage(long userId, StoredMessage msg);

    List<StoredMessage> popOfflineMessages(String clientId, int maxCount);

}
