# ai-agent-api

智能助手 AI 开放平台 - 后端服务

## 技术栈

- Java 21 LTS
- Spring Boot 4.1.0 + Spring AI 2.0.0
- MyBatis-Plus 3.5.7（团队强制 DAL 规范）
- MySQL 8.0 + Redis 7 + Elasticsearch 8 + RabbitMQ 3.13
- Flyway 数据库迁移
- springdoc OpenAPI 3
- Lombok / commons-lang3 / logstash-logback-encoder
- Spotless（google-java-format）+ Checkstyle
- Testcontainers（集成测试）

## 包结构

```
com.ai.api
├── ApiApplication.java          # 启动类（@MapperScan + @EnableAsync + @EnableScheduling）
├── common                       # 通用层（统一响应/异常/TraceId/CORS/Jackson/Async）
│   ├── config                   # CorsConfig / JacksonConfig / AsyncConfig
│   ├── exception                # ErrorCode / BusinessException
│   ├── filter                   # TraceIdFilter
│   ├── handler                  # GlobalExceptionHandler
│   └── result                   # Result<T> / PageResult<T>
├── data                         # 数据层（MyBatis-Plus）
│   ├── config                   # MybatisPlusConfig / AuditMetaObjectHandler / RabbitMqConfig
│   ├── entity                   # BaseDO / SysModelConfig / SysLlmLog
│   ├── mapper                   # SysModelConfigMapper / SysLlmLogMapper
│   └── mq                       # LlmLogConsumer
└── model                       # 模型能力层（Spring AI 封装）
    ├── config                   # ModelConfig / ChatClientFactory
    ├── controller               # ModelPingController / ModelPingReqVO
    └── service                  # ModelPingService / ModelPingResult / LlmLogService
```

## 启动步骤

### 1. 准备中间件

依赖：MySQL 8 / Redis 7 / Elasticsearch 8 / RabbitMQ 3.13（dev 默认 localhost）

```bash
# 可参考 d:\ai\docs\deployment.md 中的 docker-compose 启动中间件
docker compose up -d mysql redis elasticsearch rabbitmq
```

### 2. 设置环境变量

```bash
# DeepSeek API Key（必填，dev 测试用）
set DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxx
# 通义千问 API Key（可选，dev 测试用）
set QWEN_API_KEY=sk-xxxxxxxxxxxxxxxx
# Ollama（可选，需本地安装 Ollama 并拉取 qwen2.5:7b）
set OLLAMA_ENABLED=false
# MySQL 密码（dev 默认 root/root）
set MYSQL_PASSWORD=root
```

### 3. 启动后端

```bash
cd d:\ai\api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

启动后访问：

- API: http://localhost:8080/api/v1/model/ping
- Swagger UI: http://localhost:8080/swagger-ui.html
- 健康检查: http://localhost:8080/actuator/health
- Prometheus 指标: http://localhost:8080/actuator/prometheus
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### 4. 测试连通性

```bash
curl -X POST http://localhost:8080/api/v1/model/ping ^
  -H "Content-Type: application/json" ^
  -H "X-Trace-Id: test-trace-001" ^
  -d "{\"modelId\":\"deepseek-chat\",\"prompt\":\"你好\"}"
```

预期响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": "你好！我是 DeepSeek...",
    "modelId": "deepseek-chat",
    "latencyMs": 1234,
    "promptTokens": 10,
    "completionTokens": 50,
    "totalTokens": 60
  },
  "traceId": "test-trace-001"
}
```

## 数据库迁移

Flyway 自动在启动时执行 `src/main/resources/db/migration/V*.sql`。

当前迁移：

- `V20260818__init.sql`：创建 `sys_model_config` + `sys_llm_log`，并插入默认模型配置。

## 规范遵循

本项目严格遵循 `d:\ai\docs\企业Java后端开发规范.md`：

- **分层架构**：Controller → Service → Mapper（DAL）→ DB，单向依赖
- **对象模型**：DO / SaveReqVO / PageReqVO / RespVO / ReqDTO / RespDTO 严格分离
- **统一响应**：所有 Controller 返回 `Result<T>` 包装
- **统一异常**：Service 抛 `BusinessException(ErrorCode.XXX)`，由 `GlobalExceptionHandler` 兜底
- **逻辑删除**：`BaseDO.deleted` + `@TableLogic`，所有查询自动过滤已删除
- **审计字段**：`creator/createTime/updater/updateTime` 由 `AuditMetaObjectHandler` 自动填充
- **依赖注入**：使用 `@Resource`，禁止 `@Autowired` 字段注入
- **日志格式**：`[方法名][描述({})]`，traceId 自动注入 MDC
- **事务**：写操作 `@Transactional(rollbackFor = Exception.class)`
- **代码风格**：Spotless（google-java-format）+ Checkstyle 自动检查

## 测试

```bash
# 单元测试 + 集成测试（Testcontainers）
mvn test

# 接口测试（MockMvc）
mvn test -Dtest=ModelPingControllerTest
```

## 构建产物

```bash
mvn clean package -DskipTests
# 产物：target/api-0.0.1-SNAPSHOT.jar
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## OpenSpec Change

当前 change：`bootstrap-project`（详见 `d:\ai\openspec\changes\bootstrap-project\`）
