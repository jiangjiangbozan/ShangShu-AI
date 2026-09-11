# ====================================================================
# AI 开放平台后端 - 多阶段构建镜像
# 构建：docker build -t ai-agent-api:latest .
# 运行：docker run -p 8080:8080 --env-file .env ai-agent-api:latest
# ====================================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先复制 pom.xml 预下载依赖，利用 Docker 层缓存加速后续构建
COPY pom.xml .
RUN mvn -B dependency:go-offline -Dspotless.check.skip=true || true

# 复制源码并打包（跳过测试与 Spotless 格式检查）
COPY src ./src
RUN mvn -B clean package -DskipTests -Dspotless.check.skip=true

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 非 root 用户运行（安全规范）
RUN useradd -r -u 1001 -g root spring

# 预创建日志目录并赋权给 1001 用户。
# 必须在 USER 1001 切换前用 root 创建，否则非 root 用户无权在 /app 下建子目录，
# 导致 logback RollingFileAppender 启动时 FileNotFoundException，应用启动失败。
RUN mkdir -p /app/logs && chown -R 1001:0 /app/logs && chmod -R u+rw /app/logs

COPY --from=builder /build/target/*.jar app.jar

USER 1001
EXPOSE 8080

# JVM 参数通过 JAVA_OPTS 环境变量注入（如 -Xmx512m）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
