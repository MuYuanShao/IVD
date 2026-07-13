# =============================================================================
# IVD 智慧实验室管理系统 — 主应用 Dockerfile
# 对应 PRD §9.6 部署拓扑
# 构建: mvn clean package -pl ivd-server -am -DskipTests
# 镜像: docker build -t ivd/ivd-app:1.0.0-mvp .
# =============================================================================

FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL maintainer="IVD Team"
LABEL description="IVD Intelligent Lab Management System — Phase 1 MVP"

RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 复制 jar 包
COPY target/ivd-server-*.jar /app/ivd-server.jar

# 健康检查用
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=90s \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM 参数可在 docker-compose 中通过 JAVA_OPTS 环境变量覆盖
ENV JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -Duser.timezone=Asia/Shanghai"

ENTRYPOINT exec java ${JAVA_OPTS} -jar /app/ivd-server.jar
