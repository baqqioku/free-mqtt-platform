package com.free.zk;

import com.free.zk.config.ZkConfig;
import com.free.zk.core.ClusterInfo;
import com.free.zk.core.ServerInfo;
import com.free.zk.core.ZkClusterServerMonitor;
import com.free.zk.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class ClusterServerMonitor {

    protected static Logger logger = LoggerFactory.getLogger(ClusterServerMonitor.class);

    public static final long REFRESH_TIME_INTERVAL = 20 * 1000; //二十秒

    public String clusterName;

    protected String MQTT_CLUSTER_PATH = "/mqtt";

    protected String BROKER_PATH = "/mqtt/brokers";

    protected String defaultClusterName = "default";

    public Map<String, ClusterInfo> clusterInfoMap = new ConcurrentHashMap<>();

    protected ClusterMonitor clusterMonitor;

    public static ClusterServerMonitor createClusterServerMonitor(ZkConfig zkConfig, String clusterName) {
        return new ZkClusterServerMonitor(zkConfig,clusterName);
    }

    public abstract boolean register(ServerInfo data);

    /**
     * 初始化 ServerInfo，brokerName 从 ZooKeeper 获取
     */
    public abstract ServerInfo initServerInfo(int tcpPort,int httpPort) ;

    public abstract void unMonitor();

    public abstract boolean monitor();

    public abstract List<ServerInfo> getData();

    public abstract boolean writeData(String path,String data);

    {
        Thread clusterMonitor = new Thread(new ClusterMonitor());
        clusterMonitor.start();
    }

    public ClusterServerMonitor() {
        this.clusterName = defaultClusterName;
    }

    public ClusterServerMonitor(String clusterName) {
        this.clusterName = StringUtils.isEmpty(clusterName) ? defaultClusterName : clusterName;
    }

    public ClusterInfo getClusterInfo() {
        ClusterInfo clusterInfo = clusterInfoMap.get(clusterName);
        if (null == clusterInfo || null == clusterInfo.getServerInfoList() || clusterInfo.getServerInfoList().size() <= 0) {
            clusterInfo = addClusterInfo();
        }

        return clusterInfo;
    }

    protected ClusterInfo addClusterInfo() {
        ClusterInfo clusterInfo = clusterInfoMap.get(clusterName);
        if (null == clusterInfo) {
            synchronized (this) {
                clusterInfo = clusterInfoMap.get(clusterName);
                if (null != clusterInfo) {
                    return clusterInfo;
                }

                clusterInfo = new ClusterInfo(clusterName);
                List<ServerInfo> initData = getData();
                clusterInfo.setServerInfoList(initData);

                clusterInfoMap.put(clusterName, clusterInfo);

                for (ServerInfo serverInfo : initData) {
                    clusterInfo.getBrokerMap().put(serverInfo.getBrokerName(), serverInfo);
                }
            }
        }

        return clusterInfo;
    }


    public ClusterInfo remove() {
        return clusterInfoMap.remove(clusterName);
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    private class ClusterMonitor implements Runnable {

        private ConcurrentLinkedQueue<BaseEvent> workQueue = new ConcurrentLinkedQueue<BaseEvent>();

        @Override
        public void run() {
            while (true) {
                try {
                    if (clusterInfoMap.size() <= 0) {
                        Thread.sleep(1000 * 5);
                        continue;
                    }
                    recover();
                    flushServiceInfo();
                    Thread.sleep(2000);
                } catch (Exception e) {
                    logger.error("集群监听异常", e);
                }
            }
        }

        private void recover() throws Exception {

            BaseEvent zkEvent = null;
            while (true) {
                zkEvent = workQueue.poll();
                if (null == zkEvent) {
                    break;
                }

                if (zkEvent.getEventType() == EventTypeConst.REGISTER) {
                    dealRegisterEvent((RegisterEvent) zkEvent);
                } else if (zkEvent.getEventType() == EventTypeConst.MONITOR) {
                    dealMonitorEvent((MonitorEvent) zkEvent);
                } else if (zkEvent.getEventType() == EventTypeConst.UNMONITOR) {
                    dealUnMonitorEvent((UnMonitorEvent) zkEvent);
                }
            }

        }

        private void flushServiceInfo() {
            Iterator<Map.Entry<String, ClusterInfo>> it = clusterInfoMap.entrySet().iterator();
            while (it.hasNext()) {
                ClusterInfo clusterInfo = it.next().getValue();

                if (System.currentTimeMillis() - clusterInfo.getRefreshTime() > REFRESH_TIME_INTERVAL) {
                    List<ServerInfo> serverInfoList = getData();
                    clusterInfo.setServerInfoList(serverInfoList);
                    for (ServerInfo serverInfo : serverInfoList) {
                        clusterInfo.getBrokerMap().put(serverInfo.getBrokerName(), serverInfo);
                    }
                }
            }
        }

        private void dealUnMonitorEvent(UnMonitorEvent zkEvent) {
            monitor();
        }

        private void dealMonitorEvent(MonitorEvent zkEvent) {
            monitor();
        }

        private void dealRegisterEvent(RegisterEvent zkEvent) {
            register(zkEvent.getServerInfo());
        }

        public void addEvent(BaseEvent event) {
            workQueue.add(event);
        }
    }

    public void addZkEvent(BaseEvent zkEvent) {
        clusterMonitor.addEvent(zkEvent);
    }

}
