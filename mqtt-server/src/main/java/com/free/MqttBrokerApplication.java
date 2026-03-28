package com.free;


import com.free.mqtt.MqttServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MqttBrokerApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MqttBrokerApplication.class);

    @Autowired
    private MqttServer mqttServer;

    public static void main(String[] args){
        SpringApplication.run(MqttBrokerApplication.class, args);
        logger.info("启动Server 成功");
    }


    @Override
    public void run(String... strings) throws Exception {

        mqttServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM 关闭钩子触发，开始关闭 MqttServer...");
            try {
                mqttServer.stop();
            } catch (Exception e) {
                logger.error("关闭 MqttServer 失败", e);
            }
            logger.info("MqttServer 已关闭");
        }));
    }
}
