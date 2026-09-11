## Why

项目当前已有 `d:\ai\api`（Spring Boot 4.1 + Spring AI 2.0 单模块脚手架）和 `d:\ai\ai-agent-web`（Vue3 前端基础骨架），但缺少：
- 完整的应用配置（数据库、Redis、ES、MQ 连接）
- 统一响应封装、全局异常处理、TraceId 链路
- MyBatis 数据访问层（团队强制要求，作为后续所有业务模块的持久化基础）
- API 文档（Swagger/OpenAPI）、健康检查、监控埋点
- 代码规范工具链（checkstyle/spotless/commitlint/husky）
- 前后端联调基础（统一 API 路径、CORS、SSE 通路）

本 change 是后续所有业务 changes（对话/RAG/Agent/客服/分析/系统）的前置依赖，必须先打通"可运行的前后端基础雏形 + 大模型调用通路"，再扩展功能模块。

## What Changes

- **后端 `d:\ai\api` 完善**：
  - 引入 MyBatis（团队强制要求，作为数据访问层统一规范）+ Flyway 数据库迁移
  - 引入 springdoc-openapi 生成 API 文档
  - 引入 spring-boot-starter-actuator 暴露健康检查与 Prometheus 指标
  - 替换 `application.properties` 为 `application.yml`，含 dev/prod profile
  - 实现 `Result<T>` 统一响应、`BusinessException` + `GlobalExceptionHandler`、`ErrorCode` 枚举
  - 实现 `TraceIdFilter`，全链路日志带 traceId
  - 配置 CORS、Jackson、HikariCP、Redis、ES、RabbitMQ 基础连接
  - 添加 MyBatis 代码生成器配置与示例 Mapper
  - 引入 DeepSeek + 通义千问 + Ollama 的 Spring AI starter（打通大模型调用）
  - 添加 checkstyle.xml、spotless 配置
- **前端 `d:\ai\ai-agent-web` 完善**：
  - 配置 axios 拦截器（统一处理 traceId、错误、loading）
  - 封装 `@/api` 目录与 `request.ts` 基础请求工具
  - 实现基础布局组件（侧边栏 + 主区域）
  - 添加路由（首页、对话页占位）
  - 配置 Vite 代理，打通 `/api`、`/sse` 通路
- **工程规范**：
  - 添加 husky + lint-staged + commitlint 提交校验
  - 编写 `api/README.md`、`ai-agent-web/README.md` 补全
- **打通大模型调用**：
  - 后端 `/api/v1/model/ping` 接口：调用任一已配置模型返回响应
  - 前端"模型测试"页面：输入文本 → 调用接口 → 显示响应

## Capabilities

### New Capabilities
- `infra/bootstrap`: 项目脚手架规范，定义后端配置结构、统一响应、异常处理、TraceId、MyBatis 数据访问层、CORS、健康检查等基础约定
- `model/router`: 模型路由能力初版（仅打通调用，完整路由策略在后续 `add-model-capability` change 中扩展）

### Modified Capabilities
（无，本项目首次启动）

## Impact

- **代码**：
  - 后端：`d:\ai\api` 完善依赖、配置、common 子包、示例 Controller/Service/Mapper
  - 前端：`d:\ai\ai-agent-web` 新增 `api/`、`utils/`、`layouts/`、路由、Vite 代理
- **API**：新增 `/api/v1/model/ping`、`/actuator/health`、`/swagger-ui.html`
- **依赖**：新增 MyBatis、Flyway、springdoc、actuator、husky、commitlint、DeepSeek/Qwen/Ollama starter
- **数据库**：创建初始 Flyway 脚本 `V20260818__init.sql`（建库 + 基础表）
- **环境**：需本地或 Docker 提供 MySQL/Redis/ES/RabbitMQ（见 [deployment.md](file:///d:/ai/docs/deployment.md)）
- **后续 changes**：本 change 完成后，`implement-data-layer`、`add-model-capability`、`add-chat-module` 等可并行/串行启动

## Non-Goals

- 不实现业务功能（对话/RAG/Agent 等业务逻辑）
- 不拆分 Maven 多模块（M0 保持单模块，M3 后视情况演进）
- 不接入完整的 ModelRouter 路由策略与 fallback（仅打通调用）
- 不实现 RBAC 权限、限流、完整监控告警（在后续 changes）
- 不部署生产环境（仅本地或开发环境可运行）
