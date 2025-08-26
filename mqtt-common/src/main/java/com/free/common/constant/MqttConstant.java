package com.free.common.constant;

public class MqttConstant {


    public static int httpPort = 23240;

    public static int httpsPort = 23241;

    public static int mqttTcpPort = 23242;

    public static int mqttSslTcpPort = 23243;

    public static int mqttHttpWebSocketPort = 23244;

    public static int mqttHttpsWebSocketPort = 23245;

    public static int mqttClusterPort = 23246;


    public static final String DEFAULT_HOST = "0.0.0.0";
    public static final String  MQTT_SUBPROTOCOL_CSV_LIST = "mqtt, mqttv3.1, mqttv3.1.1";

    public static final int CHANNEL_TIMEOUT_SECONDS = 3*60;//通道保持三分钟
    public static final int SESSION_CHECK_INTERVAL = 2*60*1000;//两分钟检查一次会话状态

    public static final int AGGREGATOR_MAX_SIZE = 8*1024*1024;//字节为单位

    public static int MSG_TTL = 2*60*1000; //消息过期时间


    public static String brokerToClientTopic = "/broker/to/client/";

    public static String clientToBrokerTopic = "/client/to/broker/";


}
