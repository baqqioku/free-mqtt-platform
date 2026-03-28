package com.free.mqtt.client.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.free.common.constant.StatusEnum;
import com.free.common.resp.BaseResponse;
import com.free.mqtt.client.model.BrokerInfo;
import com.free.mqtt.client.model.UserInfo;
import okhttp3.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RouteHttpClient {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient http;

    public RouteHttpClient(String baseUrl) {
        this(baseUrl, new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build());
    }

    public RouteHttpClient(String baseUrl, OkHttpClient http) {
        this.baseUrl = baseUrl;
        this.http = http;
    }

    public UserInfo register(String userName) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userName", userName);
        JSONObject json = post("/register", body);
        JSONObject dataBody = json.getJSONObject("dataBody");
        if (dataBody == null) {
            throw new RuntimeException("register response missing dataBody");
        }
        UserInfo user = new UserInfo();
        user.setUserId(dataBody.getLong("userId"));
        user.setUserName(dataBody.getString("userName"));
        user.setToken(dataBody.getString("token"));
        return user;
    }

    public BrokerInfo login(String userName, String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userName", userName);
        body.put("token", token);
        JSONObject json = post("/login", body);
        JSONObject dataBody = json.getJSONObject("dataBody");
        if (dataBody == null) {
            throw new RuntimeException("login response missing dataBody");
        }
        BrokerInfo broker = new BrokerInfo();
        broker.setBrokerName(dataBody.getString("brokerName"));
        broker.setIp(dataBody.getString("ip"));
        Integer tcp = dataBody.getInteger("tcpPort");
        Integer httpPort = dataBody.getInteger("httpPort");
        broker.setTcpPort(tcp == null ? 0 : tcp);
        broker.setHttpPort(httpPort == null ? 0 : httpPort);
        broker.setClientId(dataBody.getString("clientId"));
        return broker;
    }

    public void offerLine(long userId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        post("/offerLine", body);
    }

    public void markBrokerDown(String brokerName) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("brokerName", brokerName);
        post("/markBrokerDown", body);
    }

    public BrokerInfo getBroker(long userId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        JSONObject json = post("/getBroker", body);
        BaseResponse<BrokerInfo> response = JSON.parseObject(json.toJSONString(), new TypeReference<BaseResponse<BrokerInfo>>(){});
        if (response.getCode() != StatusEnum.SUCCESS.getCode()) {
            throw new RuntimeException("Get broker failed: " + response.getMessage());
        }
        return response.getDataBody();
    }

    private JSONObject post(String path, Object body) throws Exception {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(JSON_MEDIA, JSON.toJSONString(body)))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("http=" + resp.code() + " path=" + path);
            }
            String content = resp.body() == null ? "" : resp.body().string();
            JSONObject json = JSON.parseObject(content);
            String code = json.getString("code");
            if (!"200".equals(code)) {
                throw new RuntimeException("api_failed code=" + code + " msg=" + json.getString("message"));
            }
            return json;
        }
    }
}

