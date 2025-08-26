package com.free.mqtt.server.concurrent;

public interface FutureCallBack<T> {

    void onSuccess(T result);   // 业务判断成功
    void onFailure(T result);   // 业务判断失败
    void onError(Throwable t);  // 异常

}
