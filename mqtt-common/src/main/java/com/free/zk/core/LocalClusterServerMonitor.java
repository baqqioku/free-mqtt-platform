package com.free.zk.core;

import com.free.zk.ClusterServerMonitor;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalClusterServerMonitor extends ClusterServerMonitor {

    private final CopyOnWriteArrayList<ServerInfo> staticServers = new CopyOnWriteArrayList<>();
    private final String defaultBrokerName;
    private final AtomicInteger brokerCounter = new AtomicInteger(1);

    public LocalClusterServerMonitor(String clusterName, List<ServerInfo> servers, String defaultBrokerName) {
        super(clusterName);
        if (servers != null && !servers.isEmpty()) {
            staticServers.addAll(servers);
        }
        this.defaultBrokerName = (defaultBrokerName == null || defaultBrokerName.trim().isEmpty())
                ? "broker-local" : defaultBrokerName.trim();
    }

    @Override
    public boolean register(ServerInfo data) {
        if (data == null) {
            return false;
        }
        boolean replaced = false;
        for (int i = 0; i < staticServers.size(); i++) {
            ServerInfo cur = staticServers.get(i);
            if (cur != null && cur.getBrokerName() != null && cur.getBrokerName().equals(data.getBrokerName())) {
                staticServers.set(i, data);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            staticServers.add(data);
        }
        return true;
    }

    @Override
    public ServerInfo initServerInfo(int tcpPort, int httpPort) {
        ServerInfo serverInfo = new ServerInfo();
        String name = defaultBrokerName;
        if (staticServers.isEmpty()) {
            name = defaultBrokerName + "-" + brokerCounter.getAndIncrement();
        }
        serverInfo.setBrokerName(name);
        serverInfo.setIp(getLocalIpAddress());
        serverInfo.setTcpPort(tcpPort);
        serverInfo.setHttpPort(httpPort);
        return serverInfo;
    }

    @Override
    public void unMonitor() {
    }

    @Override
    public boolean monitor() {
        return true;
    }

    @Override
    public List<ServerInfo> getData() {
        return new ArrayList<>(staticServers);
    }

    @Override
    public boolean writeData(String path, String data) {
        return true;
    }

    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(":") == -1) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
