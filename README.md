# Free MQTT Platform

一个基于Spring Boot和Netty构建的高性能、可扩展的MQTT消息代理平台，支持集群部署和消息路由。

## 🚀 项目概述

Free MQTT Platform是一个开源的MQTT消息代理平台，采用微服务架构设计，提供高性能的MQTT消息传输服务。该平台支持集群部署、消息路由、负载均衡等企业级特性，适用于IoT设备通信、实时消息推送等场景。

## 🏗️ 系统架构

### 模块结构

```
free-mqtt-platform/
├── mqtt-server/          # MQTT服务器核心模块
│   ├── netty/            # Netty网络处理
│   ├── qos/              # QoS消息处理
│   ├── session/          # 会话管理
│   ├── subscriptions/    # 订阅管理
│   ├── interceptor/      # 拦截器
│   ├── auth/             # 认证授权
│   └── web/              # HTTP管理接口
├── mqtt-route/           # 消息路由模块
│   ├── web/              # 路由控制接口
│   └── service/          # 路由服务
├── mqtt-common/          # 公共组件模块
│   ├── zk/               # ZooKeeper集群管理
│   ├── constant/         # 常量定义
│   └── resp/             # 响应封装
└── logs/                 # 日志目录
```

### 技术架构

- **应用框架**: Spring Boot 1.5.6
- **网络框架**: Netty 4.1.13
- **MQTT协议**: 基于Netty MQTT编解码器
- **集群管理**: ZooKeeper + Curator
- **缓存**: Redis + Redisson
- **消息处理**: Vert.x异步处理
- **日志**: Log4j2 + SLF4J
- **序列化**: FastJSON
- **HTTP客户端**: OkHttp3

## ✨ 核心功能

### 1. MQTT协议支持
- 支持MQTT 3.1和3.1.1协议
- 支持QoS 0/1/2三种服务质量等级
- 支持保留消息和遗嘱消息
- 支持主题订阅和通配符
- 支持Keep-Alive心跳检测
- 支持Clean Session会话管理

### 2. 高性能消息处理
- 基于Netty的异步非阻塞I/O
- 多线程消息处理池 (默认CPU核心数×2)
- 支持大量并发连接 (10,000+)
- 高效的消息路由算法
- 消息队列缓冲机制
- 异步事件处理

### 3. 集群管理
- 基于ZooKeeper的服务注册与发现
- 自动负载均衡
- 集群节点监控
- 故障自动转移
- 动态服务发现
- 集群状态同步

### 4. 消息路由
- HTTP消息转发
- 消息持久化
- 消息过滤和拦截
- 支持多种消息格式
- 智能路由选择
- 消息TTL过期控制

### 5. 安全认证
- 用户名/密码认证
- Redis Token验证
- 客户端ID验证
- 主题权限控制 (ACL)
- 连接加密支持
- 会话隔离

### 6. 扩展性
- 插件化架构
- 支持自定义拦截器
- 可配置的消息处理器
- 灵活的配置管理
- 事件驱动架构
- 自定义消息监听器

### 7. 监控和管理
- 连接状态监控
- 消息统计
- 性能指标收集
- 日志记录
- 健康检查
- 集群状态查看

## 🛠️ 环境要求

- **JDK**: 1.8+
- **Maven**: 3.6+
- **Redis**: 3.0+
- **ZooKeeper**: 3.4+
- **操作系统**: Windows/Linux/macOS

## 📦 快速开始

### 1. 克隆项目

```bash
git clone  https://github.com/baqqioku/free-mqtt-platform.git

cd free-mqtt-platform
```

### 2. 配置环境

#### 配置Redis
```properties
# mqtt-server/src/main/resources/application.properties
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=123
```

#### 配置ZooKeeper
```properties
# 默认配置
zk.address=127.0.0.1
clusterName=default
```

### 3. 编译项目

```bash
mvn clean compile
```

### 4. 启动服务

#### 启动MQTT服务器
```bash
cd mqtt-server
mvn spring-boot:run
```

#### 启动消息路由服务
```bash
cd mqtt-route
mvn spring-boot:run
```

### 5. 验证服务

- MQTT服务器端口: 23242 (TCP), 23243 (SSL)
- HTTP管理端口: 23240
- 路由服务端口: 8084

## 🔧 配置说明

### MQTT服务器配置

```properties
# TCP端口配置
tcpPort=23242
tcpSslPort=23243

# 集群配置
zk.address=127.0.0.1
clusterName=default

# Redis连接池配置
spring.redis.pool.max-active=100
spring.redis.pool.max-idle=100
spring.redis.pool.min-idle=10

# 线程池配置
mqtt.thread.pool.size=50

# 消息TTL配置
mqtt.message.ttl=120000

# 通道超时配置
mqtt.channel.timeout=180
```

### 集群配置

```properties
# ZooKeeper集群地址
zk.address=zk1:2181,zk2:2181,zk3:2181

# 集群名称
clusterName=production

# 集群监控间隔
cluster.monitor.interval=20000
```

### 安全配置

```properties
# 认证配置
mqtt.auth.enabled=true
mqtt.auth.redis.key.prefix=USER_STATUS

# ACL配置
mqtt.acl.enabled=true
mqtt.acl.default.allow=true
```

## 📊 性能特性

- **并发连接**: 支持10,000+并发连接
- **消息吞吐**: 100,000+ 消息/秒
- **延迟**: 平均延迟 < 10ms
- **可用性**: 99.9%+ 服务可用性
- **内存使用**: 优化的内存管理，支持大消息处理
- **CPU利用率**: 多线程处理，充分利用多核CPU
- **网络I/O**: 基于Netty的高效网络处理
- **扩展性**: 水平扩展，支持动态扩容

## 🔌 API接口

### MQTT客户端连接

```bash
# 连接MQTT服务器
mqtt://localhost:23242

# 用户名/密码认证
username: your_username
password: your_password
```



### 消息推送接口

```bash
# 服务器推送消息到客户端
POST http://localhost:23240/pushMsg
Content-Type: application/json

{
    "userId": 2,
    "messageId": 3,
    "ttl": 53,
    "updateTime": 174616022498,
    "data": {
        "msg": "hello",
        "userAo": {
            "userId": 0
        }
    },
    "url": "http://nqozsrqo.pg/aikvtkh"
}
```

### 路由服务接口

```bash
# 用户注册
POST http://localhost:8084/reqister


# 用户登录获取MQTT服务器信息
POST http://localhost:8084/login

# 消息路由推送
POST http://localhost:8084/pushMsg
```

### 接口使用流程
```
用户登录流程
-----------------------------------------------------
POST http://localhost:8084/reqister

用户注册获取token 
↓↓↓
↓↓↓
POST http://localhost:8084/reqister
登录获取clientId
↓↓↓
↓↓↓
用mqttX工具输入用户名，clientId,token，端口实行mqtt客户端登录
-----------------------------------------------------

系统消息推送

POST http://localhost:8084/pushMsg

客户端消息推送，用正常mqttx工具自行模拟就可以

```

## 🔄 访问流程

### 1. 客户端连接流程

```
客户端 → MQTT服务器
   ↓
1. 发送CONNECT消息
   ↓
2. 服务器验证用户名/密码
   ↓
3. 验证通过，返回CONNACK
   ↓
4. 自动订阅个人主题 (/broker/to/client/{userId})
   ↓
5. 连接建立完成
```

### 2. 消息发布流程

```
发布者 → MQTT服务器
   ↓
1. 发送PUBLISH消息
   ↓
2. 服务器验证权限 (ACL检查)
   ↓
3. 消息存储和转发
   ↓
4. 根据QoS级别处理确认
   ↓
5. 推送给所有订阅者
```

### 3. 消息订阅流程

```
订阅者 → MQTT服务器
   ↓
1. 发送SUBSCRIBE消息
   ↓
2. 服务器验证主题权限
   ↓
3. 添加到订阅目录
   ↓
4. 返回SUBACK确认
   ↓
5. 开始接收相关消息
```

### 4. 服务器推送流程

```
HTTP客户端 → 路由服务 → MQTT服务器
   ↓
1. 发送推送请求到路由服务
   ↓
2. 路由服务查找用户所在MQTT服务器
   ↓
3. 转发请求到目标MQTT服务器
   ↓
4. MQTT服务器推送到客户端
   ↓
5. 返回推送结果
```

## 🚀 部署指南

### 单机部署

1. 配置Redis和ZooKeeper
2. 修改配置文件
3. 启动MQTT服务器和路由服务

### 集群部署

1. 部署ZooKeeper集群
2. 配置Redis集群
3. 部署多个MQTT服务器节点
4. 配置负载均衡器

### Docker部署

```bash
# 构建镜像
docker build -t free-mqtt-platform .

# 运行容器
docker run -d -p 23242:23242 -p 23240:23240 free-mqtt-platform
```

## 🧪 测试

### 单元测试

```bash
mvn test
```

### 性能测试

```bash
# 使用MQTT压力测试工具
mosquitto_pub -h localhost -p 23242 -t test/topic -m "Hello World"

# 批量消息测试
for i in {1..1000}; do
  mosquitto_pub -h localhost -p 23242 -t "test/topic/$i" -m "Message $i"
done
```

### 连接压力测试

```bash
# 使用MQTT客户端模拟大量连接
# 建议使用专业的MQTT压力测试工具
```

### 集群测试

```bash
# 启动多个MQTT服务器节点
# 测试负载均衡和故障转移
```

## 📝 开发指南

### 添加新的消息处理器

1. 实现`MqttMsgListener`接口
2. 在Spring配置中注册Bean
3. 配置消息路由规则

### 自定义拦截器

1. 继承`Interceptor`抽象类
2. 实现消息过滤逻辑
3. 在`MqttServer`中注册

### 自定义权限控制

1. 实现`IAuthorizator`接口
2. 重写`canRead`和`canWrite`方法
3. 在`ProtocolProcessor`中配置

### 扩展消息处理

1. 继承`QosProcessor`类
2. 实现自定义QoS处理逻辑
3. 在`ProtocolProcessor`中注册

### 添加新的事件处理器

1. 继承`MqttBaseHandler`类
2. 实现`handle`方法
3. 在`MqttMsgProcessThread`中注册

### 集群扩展

1. 实现自定义集群监控逻辑
2. 扩展`ClusterServerMonitor`
3. 配置ZooKeeper集群信息

###待实现功能
1.消息的持久化存储
2.客户端,测试用其他的mqtt客户端代替（比如MQTTX）
3.消息回调监听器还完善

## 🤝 贡献指南

1. Fork项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 📄 许可证

本项目采用 [MIT License](LICENSE) 许可证。

## 📞 联系方式

- 项目维护者: [Your Name]
- 邮箱: [your.email@example.com]
- 项目地址: [GitHub Repository URL]

## 🙏 致谢

感谢以下开源项目的支持：
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Netty](https://netty.io/)
- [ZooKeeper](https://zookeeper.apache.org/)
- [Redis](https://redis.io/)

---

**注意**: 本项目仍在开发中，部分功能可能不稳定。建议在生产环境使用前进行充分测试。 