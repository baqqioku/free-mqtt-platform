package com.free.config;

import com.free.mqtt.MqttServer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;


@Deprecated
//@Component
public class MqttServerStarter implements ApplicationListener<ApplicationReadyEvent> {
    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        new MqttServer().start();
    }
}
