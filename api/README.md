# api · 尚枢 AI 后端服务

尚枢 AI（ShangShu AI）后端 —— 基于 Spring Boot 4.1 + Spring AI 2.0 的自托管 AI 应用平台服务端。按**业务域**组织包结构，每个域独立拥有 controller / service / mapper / entity / dto。

## 技术栈

- Java 21 LTS
- Spring Boot 4.1.0 + Spring AI 2.0.0（均为 GA 版本）
- MyBatis-Plus 3.5.15（团队强制 DAL 规范）+ Flyway 数据库迁移
- MySQL 8.0 + Redis 7 Stack（RediSearch）+ Elasticsearch 9 + RabbitMQ 3.13
- Spring Security 7（JWT 双 Token / CSRF / RBAC）+ Jasypt 配置加密
- springdoc OpenAPI 3 · Lombok · Spotless（google-java-format）+ Checkstyle
- Testcontainers（集成测试）

完整依赖版本以 [pom.xml](pom.xml) 为准。

## 包结构

```
com.ai.api
├── ApiApplication.java          # 启动类（@MapperScan + @EnableAsync + @EnableScheduling）
├── agent/                       # Agent 运行时：ReAct / FC 引擎、工具注册、执行追踪
├── workflow/                    # 可视化工作流：20 种 NodeExecutor、DAG 执行器、版本化
├── knowledge/                   # RAG 知识库：文档解析切片、多路检索、RRF、Rerank、QueryRewrite
├── chat/                        # 对话：SSE 流式输出、会话管理、ES 搜索索引
├── cs/                          # 智能客服：意图识别、槽位填充、澄清模板、状态机
├── course/                      # 课程知识问答：RAG 问答场景
├── model/                       # 模型管理：动态加载、连通性测试、路由降级
├── identity/                    # 身份认证：JWT、OAuth2、RBAC、API Key
├── app/                         # AI 应用：chat/rag/agent/workflow 四类型、发布与统一调用
├── prompt/                      # Prompt 模板与版本管理
├── limit/                       # 限额限流：Token 消耗限制
├── moderation/                  # 内容审查：输入输出安全检查
├── audit/                       # 操作审计：注解驱动
├── data/                        # 基础设施：MyBatis-Plus / RabbitMQ / Redis 配置、公共实体与 Mapper
└── common/                      # 通用层：统一响应 / 异常 / TraceId / CORS
```

## 启动步骤

### 1. 准备中间件

依赖：MySQL 8 / Redis 7 Stack / Elasticsearch 9 / RabbitMQ 3.13

```bash
# 在仓库根目录执行（默认值已与 application-dev.yml 对齐，无需额外配置）
docker compose up -d
```

### 2. 启动后端（dev profile，零配置可跑）

```bash
cd api
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"   # Windows
mvn spring-boot:run -Dspring-boot.run.profiles=dev          # Linux / macOS
```

> `mvn spring-boot:run` 不会读取 `.env`。需要覆盖配置时通过环境变量注入，例如模型 Key：
> `DEEPSEEK_API_KEY` / `QWEN_API_KEY`；RAG 向量化默认连接本机 Ollama（需 `ollama pull nomic-embed-text`）。

启动后访问：

- Swagger UI: http://localhost:8080/swagger-ui.html
- 健康检查: http://localhost:8080/actuator/health
- Prometheus 指标: http://localhost:8080/actuator/prometheus
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## 数据库迁移

Flyway 在启动时自动执行 `src/main/resources/db/migration/V*.sql`（`baseline-on-migrate` 已开启）。首次启动会建全量 schema；后续版本增量迁移。

## 规范遵循

本项目遵循 [AGENTS.md](AGENTS.md)（业务域模块规范 / 实体规范 / 索引规范 / 分层职责）与根目录 [../AGENTS.md](../AGENTS.md)（通用开发原则）：

- **分层架构**：Controller → Service → Mapper（DAL）→ DB，单向依赖
- **对象模型**：DO / SaveReqVO / PageReqVO / RespVO 严格分离，每个 Controller 独立 DTO 文件
- **统一响应**：所有 Controller 返回 `Result<T>`；Service 抛 `BusinessException`，`GlobalExceptionHandler` 兜底
- **逻辑删除**：`BaseDO.deleted` + `@TableLogic`；审计字段由 `AuditMetaObjectHandler` 填充（上海时区）
- **权限控制**：写接口 `@PreAuthorize`，默认最小授权
- **代码风格**：Spotless（google-java-format）+ Checkstyle，`mvnw spotless:apply` 自动格式化

## 测试

```bash
mvn test                          # 单元测试 + 集成测试（Testcontainers）
mvn test -Dtest=DagTest           # 指定用例
```

## 构建产物

```bash
mvnw.cmd clean package -DskipTests
# 产物：target/api-0.0.1-SNAPSHOT.jar
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```
