package com.free.mqtt.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class BaseThread<E> extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(BaseThread.class);

    protected BlockingQueue<E> queue;

    private volatile boolean running = true;

    public BaseThread(){
         queue = new LinkedBlockingQueue<E>();
    }

    public BaseThread(String threadName) {
        super(threadName);
        this.queue = new LinkedBlockingQueue<>();
    }

    public void submit(E event){
        if(running){
            queue.offer(event);
        }
    }

    public void shutdown(){
        running = false;
        this.interrupt();
    }

    public abstract void doing(E event);

    @Override
    public void run(){
        try{
            while(running){
                E event = queue.take();
                doing(event);
            }
        } catch (InterruptedException e) {
            logger.info("{} 线程中断退出", getName());
        }catch (Exception e){
            logger.error("{} 线程执行异常", getName(), e);
        }finally {
            logger.info("{} 已停止",getName());
        }
    }

    public BlockingQueue<E> getQueue() {
        return queue;
    }

    public void setQueue(BlockingQueue<E> queue) {
        this.queue = queue;
    }
}
