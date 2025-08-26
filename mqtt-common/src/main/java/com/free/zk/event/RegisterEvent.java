package com.free.zk.event;

import com.free.zk.core.ServerInfo;

public class RegisterEvent extends BaseEvent {

    private ServerInfo serverInfo;

    public RegisterEvent() {
        setEventType(EventTypeConst.REGISTER);
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }
}
