package com.free.zk.event;

import com.free.zk.IZkNodeListener;

public class UnMonitorEvent extends BaseEvent {

    private IZkNodeListener listener;

    public UnMonitorEvent() {
        setEventType(EventTypeConst.UNMONITOR);
    }

    public IZkNodeListener getListener() {
        return listener;
    }

    public void setListener(IZkNodeListener listener) {
        this.listener = listener;
    }
}
