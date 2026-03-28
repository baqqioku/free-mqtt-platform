# Free MQTT Platform

A Spring Boot + Netty based MQTT broker and routing platform with clustering and message reliability features.

## Modules

- `mqtt-server`: MQTT broker
- `mqtt-route`: HTTP route service
- `mqtt-common`: shared code
- `mqtt-client`: test client and CLI

## Features

- MQTT 3.1 / 3.1.1
- QoS 0 / 1 / 2
- Retained messages
- Will (LWT) with optional server-side delay
- Offline message persistence and replay (Redis)
- Cluster registration via ZooKeeper
- ACL (topic-level authorization)
- TLS/SSL listener

## Prerequisites

- JDK 8+
- Maven
- Redis
- ZooKeeper

## Build

```bash
mvn -DskipTests package
```

## Run (multi broker)

```powershell
powershell -ExecutionPolicy Bypass -File .\run-multibroker.ps1
```

Ports are printed into `logs\ports.txt`.

## Configuration (mqtt-server)

`mqtt-server/src/main/resources/application.properties`

- `tcpPort`: broker TCP port
- `tcpSslTcpPort`: broker SSL port
- `zk.address`: ZooKeeper address
- `spring.redis.*`: Redis connection

TLS:
- `mqtt.ssl.enabled=false`
- `mqtt.ssl.keystore.path=` (optional, if empty a self-signed cert is used)
- `mqtt.ssl.keystore.password=`
- `mqtt.ssl.keystore.type=JKS`
- `mqtt.ssl.needClientAuth=false`

ACL:
- `mqtt.acl.enabled=false`
- `mqtt.acl.file=` (optional, defaults to classpath `acl.conf`)
- `mqtt.acl.defaultAllow=true`
- `mqtt.acl.reloadSeconds=30`

Will delay:
- `mqtt.will.delaySeconds=0`

Cluster:
- `cluster.enabled=true`
- `cluster.local.brokerName=broker-local` (used when `cluster.enabled=false`)

Route static broker (when cluster disabled):
- `route.static.enabled=false`
- `route.static.brokerName=broker-local`
- `route.static.host=127.0.0.1`
- `route.static.tcpPort=1883`
- `route.static.httpPort=23240`

## ACL rule format

```
allow|deny  user|client|all  <name or *>  pub|sub|pubsub  <topic-filter>
```

Example:

```
allow all pubsub /broker/to/client/#
deny all pub /deny/#
```

## Test scripts

### Feature test

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-mqtt-features.ps1
```

This script builds the project, starts one broker + one route service, and verifies:

- Redis offline persistence + replay
- ACL allow/deny
- Retain behavior
- Will delay
- SSL listener (self-signed, client uses insecure trust)

### Manual client

The client app is in `mqtt-client`:

```bash
java -cp mqtt-client/target/classes;mqtt-client/target/lib/* com.free.mqtt.client.app.FreeMqttClientApp --mode subWait --routeUrl http://localhost:8084 --userName demo --topic /broker/to/client/1 --contains hello
```

SSL test example:

```bash
java -cp mqtt-client/target/classes;mqtt-client/target/lib/* com.free.mqtt.client.app.FreeMqttClientApp --mode pub --brokerUrl ssl://127.0.0.1:8883 --clientId c1 --userName u --password p --topic /allow/1 --payload hi --ssl true --sslInsecure true
```
