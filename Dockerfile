# ============================================
# Free MQTT Platform - Multi-stage Dockerfile
# ============================================

# Stage 1: Build
FROM maven:3.8.6-openjdk-8-slim AS builder

WORKDIR /build

# 先复制 pom 文件，利用 Docker 缓存加速构建
COPY pom.xml .
COPY mqtt-common/pom.xml mqtt-common/
COPY mqtt-server/pom.xml mqtt-server/
COPY mqtt-route/pom.xml mqtt-route/
COPY mqtt-client/pom.xml mqtt-client/

# 下载依赖（会被缓存）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY mqtt-common/ mqtt-common/
COPY mqtt-server/ mqtt-server/
COPY mqtt-route/ mqtt-route/
COPY mqtt-client/ mqtt-client/

# 构建，跳过测试
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM openjdk:8-jre-slim

WORKDIR /app

# 安装必要工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 创建非 root 用户
RUN groupadd -r mqtt && useradd -r -g mqtt mqtt

# 从 builder 阶段复制 jar 包
COPY --from=builder /build/mqtt-server/target/mqtt-server-1.0-SNAPSHOT.jar /app/mqtt-server.jar
COPY --from=builder /build/mqtt-route/target/mqtt-route-1.0-SNAPSHOT.jar /app/mqtt-route.jar

# 复制配置文件（可选，方便自定义配置）
COPY mqtt-server/src/main/resources/application.properties /app/config/mqtt-server.properties
COPY mqtt-route/src/main/resources/application.properties /app/config/mqtt-route.properties

# 创建日志目录
RUN mkdir -p /app/logs && chown -R mqtt:mqtt /app

USER mqtt

# 默认启动 mqtt-server
ENV SERVICE_NAME=mqtt-server
ENV SPRING_PROFILES_ACTIVE=docker

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT:-23240}/actuator/health || exit 1

# 启动脚本入口
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/${SERVICE_NAME}.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
