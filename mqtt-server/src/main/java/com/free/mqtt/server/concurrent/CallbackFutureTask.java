package com.free.mqtt.server.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class CallbackFutureTask<T> extends FutureTask<T> {

    private  FutureCallBack<T> callBack;

    public CallbackFutureTask(Callable<T> callable,FutureCallBack<T> callBack){
        super(callable);
        this.callBack = callBack;
    }

    @Override
    protected void done() {
        try {
            T result = get(); // 获取任务结果
            if (callBack != null) {
                callBack.onSuccess(result);
            }
        } catch (ExecutionException e) {
            if (callBack != null) {
                callBack.onError(e.getCause());
            }
        } catch (Exception e) {
            if (callBack != null) {
                callBack.onError(e);
            }
        }
    }

}
