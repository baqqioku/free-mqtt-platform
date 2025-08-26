package com.free.zk.event;

import com.free.zk.IZkNodeListener;

public class MonitorEvent extends BaseEvent {

    private IZkNodeListener iZkNodeListener;


    public MonitorEvent() {
        setEventType(EventTypeConst.MONITOR);
    }

    public IZkNodeListener getiZkNodeListener() {
        return iZkNodeListener;
    }

    public void setiZkNodeListener(IZkNodeListener iZkNodeListener) {
        this.iZkNodeListener = iZkNodeListener;
    }
}
