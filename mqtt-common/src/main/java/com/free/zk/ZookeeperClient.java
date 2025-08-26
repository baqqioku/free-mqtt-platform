package com.free.zk;

import com.free.zk.config.ZkConfig;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ZookeeperClient {

    private static final Logger logger= LoggerFactory.getLogger(ZookeeperClient.class);

    private ZkClient zkClient;

    private Map<String,IZkChildListener> pathListener = new ConcurrentHashMap<>();

    public ZookeeperClient(ZkConfig zkConfig){
        try {
            String address = zkConfig.getAddress();
            int connectionTimeout = zkConfig.getConnectionTimeout();
            int sessionTimeout = zkConfig.getSessionTimeout();
            zkClient = new ZkClient(address, sessionTimeout, connectionTimeout);
        }catch (Exception e){
            logger.error("zookeeper 初始化失败",e);
        }
    }

    public boolean createNode(String rootPath,String nodeName,String data){
        boolean rtv = true;

        try {
            if (!zkClient.exists(rootPath)) {
                zkClient.createPersistent(rootPath, true);
            }
            String childPath = rootPath + "/" + nodeName;


            if (zkClient.exists(childPath)) {
                zkClient.writeData(childPath, data);
                //zkClient.delete(childPath);
            } else {
                zkClient.createEphemeral(childPath, data);
            }
            logger.info("注册节点：{},{}",rootPath,childPath);

        }catch (Exception e){
            logger.error("创建节点异常",e);
        }

        return rtv;
    }

    public List<String> getChilds(String rootPath){
        return zkClient.getChildren(rootPath);
    }

    public List<String> getChildsData(String rootPath){
        List<String> childs = zkClient.getChildren(rootPath);
        if(null == childs || childs.size()<=0){
            return null;
        }

        List<String> dataList = new ArrayList<>();
        for(String node: childs){
            String fullPath = rootPath+"/"+node;
            String data= zkClient.readData(fullPath);
            dataList.add(data);
        }

        return dataList;
    }

    public String getData(String fullPath){
        return zkClient.readData(fullPath);
    }

    public boolean nodeMonitor(final String rootPath, final IZkNodeListener listener){

        boolean rtv = true;
        if(pathListener.get(rootPath) != null){
            return true;
        }
        IZkChildListener zkChildListener =  new IZkChildListener() {
            @Override
            public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {

                logger.info(parentPath);
                logger.info(currentChilds.toString());
                listener.notify(rootPath,currentChilds);

            }
        };
        zkClient.subscribeChildChanges(rootPath,zkChildListener);


        // 2. 启动时先给已有子节点注册监听
        List<String> existingChildren = zkClient.getChildren(rootPath);
        if (existingChildren != null) {
            for (String child : existingChildren) {
                String childPath = rootPath + "/" + child;
                zkClient.subscribeDataChanges(childPath, new IZkDataListener() {
                    @Override
                    public void handleDataChange(String dataPath, Object data) throws Exception {
                        logger.info("节点数据变更：{}",data.toString());
                        listener.notifyDataChange(dataPath,data);
                    }

                    @Override
                    public void handleDataDeleted(String dataPath) throws Exception {
                        logger.info("节点删除：{}",dataPath);
                        listener.notifyDataDeleted(dataPath);
                    }
                });
            }
        }

        pathListener.put(rootPath,zkChildListener);
        return rtv;

    }

    public void cancelMonitor(String rootPath) {
        pathListener.remove(rootPath);
        zkClient.unsubscribeChildChanges(rootPath, pathListener.get(rootPath));
    }

    public void writeData(String path,String data){
        zkClient.writeData(path,data);
    }

    /**
     * 从 ZooKeeper 获取下一个 broker 编号
     */
    public int getNextBrokerId(String brokersPath) {

        if (!zkClient.exists(brokersPath)) {
            zkClient.createPersistent(brokersPath, true);
            return 1;
        }
        List<String> children = zkClient.getChildren(brokersPath);
        int maxId = 0;
        for (String child : children) {
            if (child.startsWith("broker-")) {
                try {
                    int id = Integer.parseInt(child.substring("broker-".length()));
                    maxId = Math.max(maxId, id);
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxId + 1;
    }






}
