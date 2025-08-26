/*
 * Copyright (c) 2012-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package com.free.mqtt.server.interceptor;

import com.free.mqtt.server.auth.AuthChannel;
import com.free.mqtt.server.netty.MqttNettyChannel;
import com.free.mqtt.server.session.data.StoredMessage;
import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * 回调的所有接口不能够阻塞，否则会影响netty的io线程
 * @author Administrator
 *
 */
public interface Interceptor {
	
	boolean filterMsg(String ip, StoredMessage pubMsg);//等同  msglistener  routeRequest
	
    void notifySendMsgOk(String clientId, String topic, MqttQoS qos, Integer pushMessageId);
    
    void notifyDisconnect(String ip, String clientId);
    
    void heartbeat(String clientId);
    
    void checkValid(AuthChannel authChannel);
    
    boolean checkTtl(long createTime, long ttl);
    
    String autoSub(String clientId);

    void startPushMsgTimeTask(RetryPushTimerTask task);

    void cancelPushMsgTimeTask(String clientId, int msgId);
}
