# 部署与运维手册

> **版本**：v1.0
> **更新日期**：2026-08-18
> **关联**：[architecture.md](./architecture.md)、[changes-roadmap.md](./changes-roadmap.md)

---

## 1. 部署架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                       Docker Host (单机/集群)                         │
│                                                                     │
│  ┌─────────────┐                                                    │
│  │   Nginx     │ :80/:443                                          │
│  │  (前端静态+  │                                                   │
│  │   反向代理) │                                                   │
│  └──────┬──────┘                                                   │
│         │                                                           │
│  ┌──────▼──────┐         ┌──────────────┐                          │
│  │   api       │ ──────► │   MySQL      │ :3306                   │
│  │ (Spring Boot│         └──────────────┘                          │
│  │  :8080)     │ ──────► ┌──────────────┐                          │
│  │             │         │   Redis      │ :6379                   │
│  │             │ ──────► └──────────────┘                          │
│  │             │         ┌──────────────┐                          │
│  │             │ ──────► │Elasticsearch │ :9200                    │
│  │             │         └──────────────┘                          │
│  │             │ ──────► ┌──────────────┐                          │
│  │             │         │  RabbitMQ    │ :5672/:15672             │
│  │             │         └──────────────┘                          │
│  │             │ ──────► ┌──────────────┐                          │
│  │             │         │   Ollama     │ :11434（本地模型）        │
│  └─────────────┘         └──────────────┘                          │
│                                                                     │
│  ┌──────────────────────────────────────────────────┐               │
│  │  监控栈：Prometheus + Grafana + Loki + Promtail   │               │
│  └──────────────────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. 环境要求

### 2.1 硬件（生产推荐）

| 组件 | CPU | 内存 | 磁盘 |
|------|-----|------|------|
| 应用服务器（api + nginx） | 4 核 | 8 GB | 50 GB SSD |
| MySQL | 4 核 | 8 GB | 200 GB SSD |
| Redis | 2 核 | 4 GB | 20 GB |
| Elasticsearch | 4 核 | 16 GB | 200 GB SSD |
| RabbitMQ | 2 核 | 4 GB | 20 GB |
| Ollama（本地模型，可选） | 8 核 | 32 GB | 100 GB（含模型） |
| 监控栈 | 2 核 | 4 GB | 50 GB |

### 2.2 软件

- Docker 24.x+
- Docker Compose 2.20+
- Java 21（仅构建镜像需要）
- Node.js 22+（仅前端构建需要）

### 2.3 外部依赖

- DeepSeek API Key
- 通义千问 API Key
- 和风天气 API Key
- Tavily API Key（网络搜索）

---

## 3. Docker 镜像构建

### 3.1 后端镜像（`api/Dockerfile`）

```dockerfile
# 多阶段构建：maven 官方镜像编译，JRE 镜像运行
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
# 注入阿里云 Maven 镜像（.mvn/settings-docker.xml）：国内网络直连 Central 易超时
COPY .mvn/settings-docker.xml /root/.m2/settings.xml
COPY src ./src
RUN mvn -B clean package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 -g root spring
RUN mkdir -p /app/logs && chown -R 1001:0 /app/logs && chmod -R u+rw /app/logs
COPY --from=builder /build/target/*.jar app.jar
USER 1001
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
```

> JVM 参数通过 `JAVA_OPTS` 环境变量注入（如 `-Xms512m -Xmx2g -XX:+UseG1GC`）。

### 3.2 前端构建

前端构建为静态资源，由 Nginx 容器托管：

```bash
cd ai-agent-web/
npm ci
npm run build-only   # 产物在 dist/
```

### 3.3 Nginx 镜像

`nginx/Dockerfile`：
```dockerfile
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY ssl/ /etc/nginx/ssl/
COPY ../ai-agent-web/dist /usr/share/nginx/html
```

---

## 4. Docker Compose 编排

文件：`docker-compose.yml`

```yaml
version: '3.9'

services:
  # ============ 基础设施 ============
  mysql:
    image: mysql:8.0
    container_name: ai-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ai_agent
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./mysql/init:/docker-entrypoint-initdb.d
      - ./mysql/conf/my.cnf:/etc/mysql/conf.d/my.cnf
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: ai-redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 1gb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.14.0
    container_name: ai-es
    restart: unless-stopped
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms2g -Xmx2g
      - cluster.name=ai-es-cluster
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: ai-rabbitmq
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 15s
      timeout: 10s
      retries: 5

  ollama:                    # 可选：本地模型
    image: ollama/ollama:latest
    container_name: ai-ollama
    restart: unless-stopped
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]

  # ============ 应用 ============
  api:
    build:
      context: ./api
      dockerfile: Dockerfile
    container_name: ai-api
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      elasticsearch:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ai_agent?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      AI_MODEL_DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
      AI_MODEL_QWEN_API_KEY: ${QWEN_API_KEY}
      AI_MODEL_OLLAMA_BASE_URL: http://ollama:11434
      TZ: Asia/Shanghai
    ports:
      - "8080:8080"
    volumes:
      - ./api/uploads:/app/uploads
      - ./api/logs:/app/logs

  nginx:
    image: nginx:1.27-alpine
    container_name: ai-nginx
    restart: unless-stopped
    depends_on:
      - api
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - ./ai-agent-web/dist:/usr/share/nginx/html:ro
      - nginx-logs:/var/log/nginx

  # ============ 监控 ============
  prometheus:
    image: prom/prometheus:latest
    container_name: ai-prometheus
    restart: unless-stopped
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus

  grafana:
    image: grafana/grafana:latest
    container_name: ai-grafana
    restart: unless-stopped
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD}
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources

  loki:
    image: grafana/loki:latest
    container_name: ai-loki
    restart: unless-stopped
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml
    volumes:
      - loki-data:/loki

  promtail:
    image: grafana/promtail:latest
    container_name: ai-promtail
    restart: unless-stopped
    volumes:
      - ./api/logs:/var/log/api
      - ./nginx/logs:/var/log/nginx
      - ./monitoring/promtail.yml:/etc/promtail/config.yml:ro
    command: -config.file=/etc/promtail/config.yml

volumes:
  mysql-data:
  redis-data:
  es-data:
  rabbitmq-data:
  ollama-data:
  nginx-logs:
  prometheus-data:
  grafana-data:
  loki-data:
```

### 环境变量（`.env`，示例模板）

```env
# MySQL
MYSQL_ROOT_PASSWORD=<change-me>
MYSQL_USER=aiagent
MYSQL_PASSWORD=<change-me>

# Redis
REDIS_PASSWORD=<change-me>

# RabbitMQ
RABBITMQ_USER=aiagent
RABBITMQ_PASSWORD=<change-me>

# LLM API Keys
DEEPSEEK_API_KEY=<your-deepseek-key>
QWEN_API_KEY=<your-qwen-key>

# 监控
GRAFANA_PASSWORD=<change-me>
```

---

## 5. Nginx 配置

文件：`nginx/nginx.conf`

```nginx
worker_processes auto;

events {
    worker_connections 10240;
}

http {
    include       mime.types;
    default_type  application/octet-stream;

    # SSE/WebSocket 长连接关键配置
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    keepalive_requests 1000;

    # gzip 压缩
    gzip on;
    gzip_min_length 1k;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
    gzip_vary on;

    # 上游 api 服务
    upstream api_backend {
        server api:8080;
        keepalive 32;
    }

    # HTTP -> HTTPS 重定向
    server {
        listen 80;
        server_name _;
        return 301 https://$host$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name your-domain.com;

        ssl_certificate /etc/nginx/ssl/server.crt;
        ssl_certificate_key /etc/nginx/ssl/server.key;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;

        # 前端静态资源
        root /usr/share/nginx/html;
        index index.html;

        # 前端路由（history 模式）
        location / {
            try_files $uri $uri/ /index.html;
        }

        # REST API
        location /api/ {
            proxy_pass http://api_backend;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header Connection "";
        }

        # SSE 流式接口（关键：禁用 buffer）
        location ~ ^/api/v1/(chat|agent|kb|cs|da)/.*stream$ {
            proxy_pass http://api_backend;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Accel-Buffering no;   # 禁用 nginx 缓存
            proxy_buffering off;
            proxy_cache off;
            proxy_read_timeout 300s;                 # 流式超时 5 分钟
            proxy_send_timeout 300s;
            chunked_transfer_encoding on;
            add_header X-Accel-Buffering no;
        }

        # WebSocket（如客服转人工等）
        location /ws/ {
            proxy_pass http://api_backend;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_read_timeout 3600s;
        }

        # 文件上传（知识库文档）
        location /api/v1/kb/documents/upload {
            client_max_body_size 100m;
            proxy_pass http://api_backend;
            proxy_set_header Host $host;
        }

        # 日志
        access_log /var/log/nginx/access.log;
        error_log /var/log/nginx/error.log;
    }
}
```

---

## 6. 监控配置

### 6.1 Prometheus（`monitoring/prometheus.yml`）

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api:8080']

  - job_name: 'mysql'
    static_configs:
      - targets: ['mysql-exporter:9104']

  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']

  - job_name: 'rabbitmq'
    static_configs:
      - targets: ['rabbitmq:15692']

  - job_name: 'elasticsearch'
    static_configs:
      - targets: ['elasticsearch:9208']
```

### 6.2 Spring Boot Actuator

后端 `application.yml`：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ai-agent-api
    export:
      prometheus:
        enabled: true
```

### 6.3 Grafana Dashboards

预设仪表盘（存于 `monitoring/grafana/dashboards/`）：
- **API Overview**：QPS、P95/P99 延迟、错误率、JVM
- **LLM**：调用量、Token 用量、各模型延迟
- **RAG**：检索延迟、召回数
- **Agent**：执行次数、迭代次数分布、失败率
- **Infra**：MySQL/Redis/ES/RabbitMQ 关键指标

### 6.4 Loki 日志聚合

`monitoring/promtail.yml`：
```yaml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: api
    static_configs:
      - targets: [localhost]
        labels:
          job: api
          app: ai-agent-api
          __path__: /var/log/api/*.log
  - job_name: nginx
    static_configs:
      - targets: [localhost]
        labels:
          job: nginx
          __path__: /var/log/nginx/*.log
```

### 6.5 告警规则（`monitoring/alerts.yml`）

```yaml
groups:
  - name: api
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.05
        for: 5m
        labels: { severity: critical }
        annotations: { summary: "API 错误率 > 5%" }

      - alert: LlmApiError
        expr: rate(ai_llm_calls_total{status="error"}[5m]) > 0.1
        for: 3m
        labels: { severity: warning }
        annotations: { summary: "LLM 调用错误率 > 10%" }

      - alert: SseLatencyHigh
        expr: histogram_quantile(0.95, rate(ai_sse_first_token_seconds_bucket[5m])) > 2
        for: 5m
        labels: { severity: warning }
        annotations: { summary: "SSE 首 token 延迟 P95 > 2s" }

      - alert: AgentTaskFail
        expr: rate(ai_agent_tasks_total{status="failed"}[10m]) / rate(ai_agent_tasks_total[10m]) > 0.1
        for: 10m
        labels: { severity: warning }
        annotations: { summary: "Agent 任务失败率 > 10%" }

      - alert: EsUnavailable
        expr: elasticsearch_cluster_health_status{color="red"} == 1
        for: 2m
        labels: { severity: critical }
        annotations: { summary: "Elasticsearch 集群状态 red" }
```

---

## 7. 部署流程

### 7.1 首次部署

```bash
# 1. 准备配置
cd <仓库根目录>
cp .env.example .env
# 编辑 .env 填入密钥

# 2. 构建前端
cd ai-agent-web
npm ci
npm run build-only
cd ..

# 3. 构建并启动
docker compose --env-file .env build
docker compose --env-file .env up -d

# 4. 初始化数据库（Flyway 自动执行）
# 5. 验证
docker compose ps
curl http://localhost/actuator/health
```

### 7.2 滚动更新

```bash
# 拉取新镜像
docker compose pull api

# 滚动重启（无停机）
docker compose up -d --no-deps api

# 验证
docker compose logs -f api
```

### 7.3 回滚

```bash
# 使用前一版本镜像
docker compose down api
docker tag ai-api:previous ai-api:latest
docker compose up -d api
```

### 7.4 数据库迁移

Flyway 在应用启动时自动执行迁移。如需手动：
```bash
docker compose exec api java -cp app.jar org.flywaydb.core.Flyway migrate
```

---

## 8. 运维手册（Runbooks）

### 8.1 LLM API 不可用

**触发**：`LlmApiError` 告警
**影响**：对话/Agent 功能受限
**步骤**：
1. 检查 DeepSeek/通义千问 API 状态页
2. 切换 `ModelRouter` 默认模型到 Ollama 本地
3. 通知用户部分功能降级
4. 恢复后切回

### 8.2 ES 集群异常

**触发**：`EsUnavailable`
**步骤**：
1. `docker compose logs elasticsearch`
2. 检查磁盘空间
3. 必要时重启：`docker compose restart elasticsearch`
4. 验证：`curl http://localhost:9200/_cluster/health`

### 8.3 RabbitMQ 消息堆积

**步骤**：
1. 查看管理界面 `http://localhost:15672`
2. 找到堆积队列
3. 检查消费者日志
4. 必要时扩容消费者（增加 api 副本）

### 8.4 磁盘满

**步骤**：
1. `docker system prune -a`（清理无用镜像）
2. 清理旧日志：`docker compose exec api find /app/logs -mtime +30 -delete`
3. 清理 ES 旧索引
4. 扩容磁盘

---

## 9. 备份与恢复

### 9.1 MySQL 备份

```bash
# 每日凌晨自动备份（cron）
docker compose exec mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} ai_agent | gzip > backups/mysql-$(date +%Y%m%d).sql.gz

# 恢复
gunzip < backups/mysql-20260818.sql.gz | docker compose exec -T mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} ai_agent
```

### 9.2 ES 快照

配置 ES 仓库后定期快照（见 ES 官方文档）。

### 9.3 Redis 持久化

已配置 RDB + AOF，volume 持久化。

### 9.4 上传文件

`./api/uploads/` 目录需独立备份。

---

## 10. 性能调优

### 10.1 JVM

```
-Xms2g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs/heapdump
```

### 10.2 MySQL

```ini
# my.cnf
innodb_buffer_pool_size = 4G
innodb_log_file_size = 512M
innodb_flush_log_at_trx_commit = 2
max_connections = 500
```

### 10.3 Elasticsearch

```yaml
# 性能调优
indices.fielddata.cache.size: 30%
thread_pool.search.queue_size: 1000
```

### 10.4 应用层
- SSE 连接池：Tomcat `server.tomcat.threads.max=400`
- HikariCP：`maximum-pool-size=30`
- Redis 连接池：Lettuce 默认即可

---

## 11. 安全加固

- 所有服务密码使用强随机值
- 容器以非 root 用户运行（Dockerfile 添加 `USER`）
- 限制容器网络：仅暴露必要端口
- MySQL/Redis/ES/RabbitMQ 不对公网开放，仅在内网通信
- API 启用 HTTPS
- 配置 CORS 白名单
- 启用速率限制（Nginx `limit_req`）

---

## 12. 文档与脚本位置

| 文件 | 位置 |
|------|------|
| Docker Compose | `docker-compose.yml`（M7 创建） |
| 后端 Dockerfile | `api/\Dockerfile`（M7 创建） |
| Nginx 配置 | `nginx/nginx.conf`（M7 创建） |
| Prometheus 配置 | `./monitoring\prometheus.yml`（M7 创建） |
| Grafana 仪表盘 | `./monitoring\grafana\`（M7 创建） |
| 告警规则 | `./monitoring\alerts.yml`（M7 创建） |
| 运维手册 | `./docs\runbooks\`（M7 创建） |
| 环境变量模板 | `./.env.example`（M7 创建） |

> M7 阶段由 OpenSpec change `prepare-deployment` 落地所有文件。
