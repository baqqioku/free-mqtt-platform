package com.free.mqtt.server.concurrent;

import java.util.concurrent.*;

public class AsyncThreadPoolExecutor {

    private int eventAsyncTaskThreadNum = Runtime.getRuntime().availableProcessors()*2;

    private ThreadPoolExecutor threadPoolExecutor;

    public AsyncThreadPoolExecutor(int eventAsyncTaskThreadNum){
        if(eventAsyncTaskThreadNum>0){
            this.eventAsyncTaskThreadNum = eventAsyncTaskThreadNum;
        }

        threadPoolExecutor = new ThreadPoolExecutor(this.eventAsyncTaskThreadNum, this.eventAsyncTaskThreadNum, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    public AsyncThreadPoolExecutor(int eventAsyncTaskThreadNum, int queueCapacity){
        if(eventAsyncTaskThreadNum > 0){
            this.eventAsyncTaskThreadNum = eventAsyncTaskThreadNum;
        }
        BlockingQueue<Runnable> queue = queueCapacity > 0 ? new LinkedBlockingQueue<>(queueCapacity) : new LinkedBlockingQueue<>();
        threadPoolExecutor = new ThreadPoolExecutor(this.eventAsyncTaskThreadNum, this.eventAsyncTaskThreadNum, 30, TimeUnit.SECONDS, queue);
    }

    public Future<?> submit(FutureTask<?> task){
        return threadPoolExecutor.submit(task);
    }

    public Future<?> submit(Runnable task){
        return threadPoolExecutor.submit(task);
    }

    public void destroy(){
        threadPoolExecutor.shutdownNow();
    }

    public int getPoolSize(){
        return threadPoolExecutor.getPoolSize();
    }


}
