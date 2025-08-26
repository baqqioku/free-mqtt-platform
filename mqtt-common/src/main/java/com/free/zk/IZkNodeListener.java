package com.free.zk;

import java.util.List;

public interface IZkNodeListener {

    public void notify(String rootPath, List<String> childs);

    public void notifyDataChange(String dataPath, Object data);

    public void notifyDataDeleted(String dataPath);
}
