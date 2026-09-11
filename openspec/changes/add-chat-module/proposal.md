## Why

平台当前仅具备模型连通性测试（`ModelPingController`）的原子能力，缺少完整的对话交互模块。用户无法进行多轮对话、无法管理会话上下文，也无法通过 SSE 实时接收 AI 流式响应。同时，大模型 API 配置（API Key、base URL、模型参数）当前硬编码在 `application-dev.yml` 中，修改需重启应用，无法满足生产环境动态运维需求。基础设施（DB/Redis/ES/MQ）虽有配置但缺乏统一的准备方案和验证方法。本 change 一次性解决这三个核心问题，为后续 RAG、Agent 等高级模块奠定对话基座。

## What Changes

- **新增对话聊天模块**：实现会话管理（创建/列表/删除）、消息管理（发送/历史/上下文窗口）、SSE 流式响应（`Flux<ServerSentEvent>`），前端打字机效果 + Markdown 渲染
- **新增大模型 API 动态配置**：API 配置管理界面（CRUD）、连接测试（实时 ping 验证）、配置加密存储（Jasypt 或 AES 对称加密）、动态加载机制（`RefreshScope` 或 Redis 配置中心，无需重启）
- **基础设施准备方案**：MySQL 建表脚本（chat_session/chat_message）、Redis 键策略（会话上下文/用户 token 配额）、ES 日志索引模板、RabbitMQ 消息队列（异步日志写入）、完整验证脚本
- **BREAKING**：无破坏性变更。chat 模块为全新模块，model/config 对现有 `ModelPingController` 无侵入

## Capabilities

### New Capabilities
- `chat`: 对话聊天模块 — 会话管理、消息管理、SSE 流式响应、上下文窗口管理、前端打字机效果
- `model/config`: 大模型 API 动态配置 — 配置 CRUD、连接测试、加密存储、动态加载、Provider 注册表

### Modified Capabilities
<!-- 无现有 capability 的 spec 级行为变更 -->

## Impact

- **后端新增包**：`com.ai.api.chat`（controller/service/entity/mapper/dto）、`com.ai.api.model.config`（扩展）
- **数据库新增表**：`chat_session`（会话表）、`chat_message`（消息表），通过 Flyway 迁移
- **Redis 新增键**：`chat:ctx:{sessionId}`（会话上下文）、`chat:token:{userId}`（用户配额）
- **ES 新增索引**：`chat_message_log`（消息日志索引，用于后续审计与分析）
- **RabbitMQ 新增队列**：`chat.message.log`（异步消息日志写入）
- **前端新增页面**：`/chat`（对话主页，侧边栏会话列表 + 主对话区 + SSE 流式输出）
- **前端新增依赖**：`@microsoft/fetch-event-source`（SSE 客户端）、`marked`（Markdown 渲染，已存在）
- **后端新增依赖**：`jasypt-spring-boot-starter`（配置加密）、`spring-boot-starter-webflux`（仅用于 `Flux` SSE 响应，非 WebFlux 全栈）
- **配置变更**：`application-dev.yml` 新增 `model.providers` 动态配置段、`chat.*` 会话配置段