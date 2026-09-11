<p align="center">
  <h1 align="center">尚枢 AI · ShangShu AI</h1>
  <p align="center">开放式 AI 应用编排与 Agent 运行平台 — 让每个 AI 应用都触手可及、可编排、可运行。</p>
</p>

尚枢 AI（ShangShu AI）是面向企业、开发者的**开放式 AI 应用编排与 Agent 运行平台**。它以服务端编排为核心，深度借鉴 Dify 的工作流 / DSL 设计与 Operit 的端侧智能交互体验，将大语言模型对话、知识库 RAG 检索增强、Agent ReAct 推理、可视化工作流编排、智能客服、数据分析等能力，统一收敛为一个**可配置、可复用、可运营**的中枢平台，并以 Web / Android / 小程序三端交付。

- **尚**：崇尚、向上 —— 以 AI 能力为尚，能力指数级向上；
- **枢**：枢纽、中枢 —— 将多模型、知识库、Agent、工作流、工具拧合为一个可调度的运行中枢。

> 理念：不同于简单封装大模型，尚枢把「模型 → 知识 → 编排 → 工具 → 交互」全链路做成一个可运营的 AI 中枢。

---

## 功能特性

- **多模型聚合** — 统一抽象多供应商模型，Token 分层统计，流式输出归一化，一处配置多端复用；支持 DeepSeek / 通义 / Ollama 等。
- **知识库 RAG** — 多路召回 + RRF 融合检索，Redis 命中缓存，长尾问句召回更精准；建设「检索 → 评价 → 反馈」运营闭环。
- **Agent 编排** — Agent ReAct 推理引擎 + 支持条件分支 / 变量插值的 DAG 工作流引擎，RabbitMQ 异步任务派发、心跳超时检测、上下文快照断点续跑。
- **可视化工作流** — Dify 风格的可视化编排器（节点画布 / 最小图 / 自定义边），支持 20 种节点类型。
- **安全合规** — JWT 双 Token + CSRF Double-Submit + RBAC 权限体系，三端统一鉴权，多租户数据隔离。
- **数据与运营** — 智能客服工作台、数据分析（NL2SQL）、SSE 流式对话、全链路 Trace 与日志审计。
- **多端交付** — Web 管理台、Android 移动端、uni-app 小程序端一鱼多吃。

---

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Java 21 · Spring Boot 4.1 · Spring AI 2.0 · Spring Security · MyBatis-Plus · Flyway |
| 中间件 | MySQL 8 · Redis 7 (redis-stack) · Elasticsearch 9 · RabbitMQ |
| Web 端 | Vue 3 · TypeScript · Vite · Element Plus · Pinia · Vue Flow · ECharts |
| Android 端 | Kotlin · Jetpack Compose · Material 3 · Retrofit · kotlinx.serialization |
| 小程序端 | uni-app · Vue 3 · TypeScript |
| 基础设施 | Docker Compose · Nginx · GitLab CI/CD（参考） |

---

## 仓库结构

```
ai/
├── api/                    # 后端服务（Spring Boot + Spring AI）
│   └── src/main/java/com/ai/api/
│       ├── common/         # 通用层：统一响应/异常/TraceId/CORS/异步
│       ├── data/           # 数据层：MyBatis-Plus 实体/Mapper/MQ 消费
│       └── model/          # 模型能力层：Spring AI 封装 / 模型连通性测试
├── ai-agent-web/           # Web 管理台（Vue 3 + Element Plus + Vue Flow）
├── ai-agent-client/        # Android 移动端（Kotlin + Jetpack Compose）
├── ai-agent-mini/          # 小程序端（uni-app）
├── docker-compose.yml      # 一键部署（MySQL / Redis / ES / RabbitMQ）
└── docs/                   # 文档（默认忽略，不随本次公开仓库上传）
```

---

## 快速开始

### 1. 启动中间件

依赖：MySQL 8 / Redis 7 / Elasticsearch 9 / RabbitMQ。

```bash
docker compose up -d mysql redis elasticsearch rabbitmq
```

> API Key：`DEEPSEEK_API_KEY`、`QWEN_API_KEY`、`OLLAMA_ENABLED` 等请写入根目录 `.env`（参考 `.env.example`，真实 `.env` 已忽略）。

### 2. 启动后端

```bash
cd api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 健康检查：`http://localhost:8080/actuator/health`
- Prometheus：`http://localhost:8080/actuator/prometheus`

### 3. 启动 Web 端

```bash
cd ai-agent-web
npm install        # 依赖安装请在后端环境外单独执行
npm run dev
```

### 4. Android / 小程序端

- **Android**：`ai-agent-client`，用 Android Studio 打开，对接后端地址见 `app/build.gradle.kts` 的 `API_BASE_URL`。
- **小程序**：`ai-agent-mini`，用 HBuilderX 导入 uni-app 工程，配置后端地址后运行。

---

## 文档与演进

- 后端 API 文档：见 `api/README.md`。
- 架构说明与部署指引：见 `docs/architecture.md`、`docs/deployment.md`（默认未随公开仓库上传，可根据需要单独开放）。

本项目在设计上对标并借鉴了业界标杆：
- **Dify**：工作流可视编排、DSL、Agent 与 RAG 的产品形态；
- **Operit**：端侧多 Provider、本地记忆、工具系统与富交互消息渲染。

**Roadmap**：长期记忆 → 角色卡 → 语音交互 → 技能市场 → 端侧本地模型与自动化。

---

## 许可证

本项目当前为私有 / 内部分支管理，许可证与对外发布策略待定（如需开源，请补充 LICENSE 并调整仓库可见性）。

© 尚枢 AI (ShangShu AI)。保留所有权利。