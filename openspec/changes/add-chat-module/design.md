## Context

平台现状：`bootstrap-project` 已落地 Spring Boot 4.1 + Spring AI 2.0 + MyBatis-Plus 3.5.15 + Flyway + Redis + ES + RabbitMQ，`add-user-auth` 已落地 JWT 认证 + Spring Security 7。现有 `ChatClientFactory` 为 DeepSeek/Qwen/Ollama 分别注册了 `ChatClient` Bean，但配置硬编码在 `application-dev.yml` 中，无法动态变更。模型层仅有 `ModelPingController` 的连通性测试，缺少完整的对话交互能力。本设计在此基础上叠加对话模块、动态配置、基础设施完善三大能力。

## Goals / Non-Goals

**Goals:**
- 建立 `com.ai.api.chat` 子域，实现会话管理 + 消息发送 + SSE 流式响应 + 上下文窗口管理
- 建立 `com.ai.api.model.config` 子域，实现模型配置 CRUD + 连接测试 + 加密存储 + 动态加载
- 提供基础设施准备方案（建表脚本、Redis 键策略、ES 索引模板、MQ 队列）
- 前端落地对话页面（侧边栏 + 主对话区 + SSE 打字机 + Markdown）和模型配置管理页面
- 使用 Spring AI `ChatMemory`（Redis 实现）管理对话上下文，复用现有 `chat-memory-repository-redis`

**Non-Goals:**
- 不引入 WebFlux 全栈 — 仅使用 `spring-boot-starter-webflux` 的 `Flux` 类型支持 SSE，Controller 保持 MVC 风格
- 不做 Function Calling / Tool 调用 — 属于 Agent 模块范围
- 不做多模态（图片/文件上传）— 基础对话模块仅处理文本
- 不做会话共享 / 团队协作 — 会话仅归属单个用户
- 不做 RAG 问答 — 属于 `add-rag-module` 范围
- 不做模型参数精细调优（temperature/top_p 等 UI 调整）— 使用配置文件默认值，后续在 `add-model-capability` 中完善

## Decisions

### D1：SSE 实现方式：`Flux<ServerSentEvent>` vs `SseEmitter`

**选择**：`Flux<ServerSentEvent<String>>`（Spring WebFlux 的 Reactive 类型）

**理由**：
- Spring AI 2.0 的 `ChatClient.prompt().stream().content()` 返回 `Flux<String>`，天然适配 Reactive 流
- `Flux` 支持背压（backpressure），当客户端消费慢于生产时自动降速
- `SseEmitter` 为 Servlet 3.0 异步 API，与 Spring AI 的 `Flux` 需要额外适配（`Flux.subscribe()`），不如直接返回 `Flux` 简洁
- 只需引入 `spring-boot-starter-webflux` 依赖即可使用 `Flux` 作为返回值类型，Controller 本身仍为 MVC 注解式

**备选**：`SseEmitter`，被否决原因：与 Spring AI `Flux` 流适配成本高，需手动管理 `onComplete`/`onError`/`onTimeout`。

### D2：对话上下文管理：Spring AI ChatMemory（Redis） vs 自建上下文服务

**选择**：Spring AI `ChatMemory` + `chat-memory-repository-redis`（已引入依赖）

**理由**：
- Spring AI 提供 `ChatMemory` 接口，`RedisChatMemory` 实现自动将对话历史存入 Redis
- `MessageChatMemoryAdvisor` 可在每次 `ChatClient` 调用时自动注入上下文，无需手动拼接
- 复用现有 `spring-ai-starter-chat-memory-repository-redis` 依赖，零额外成本
- Redis 数据自动过期（TTL = 会话超时时间），避免内存泄漏

**备选**：自建上下文服务，每次从 MySQL 查最近 N 轮消息拼装为 Prompt。被否决原因：重复造轮子，Spring AI 已内置完善方案。

### D3：API Key 加密存储：Jasypt vs AES-256-GCM 自实现

**选择**：Jasypt 3.0.5 + 主密钥通过环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 注入

**理由**：
- Jasypt 是 Spring Boot 生态的标准加密方案，`jasypt-spring-boot-starter` 自动解密 `ENC(...)` 格式的配置值
- 支持 `@EnableEncryptableProperties` 注解，无需修改现有代码
- 主密钥不入仓库（环境变量注入），符合安全最佳实践
- 内置 AES-256-GCM 算法，性能与安全性兼顾

**备选**：AES-256-GCM 自实现加密工具类，被否决原因：需自行管理 IV、密钥轮换、与 Spring 配置体系集成，维护成本高。Jasypt 已解决上述所有问题。

### D4：配置动态加载：`@RefreshScope` vs Redis 配置中心

**选择**：自定义 `ModelProviderRegistry` + `ApplicationEventPublisher` 发布 `ModelConfigChangedEvent`

**理由**：
- `@RefreshScope` 依赖 Spring Cloud Config，引入额外基础设施
- Redis 配置中心需额外中间件依赖
- 自定义 Registry 模式：`ModelConfigService` 保存配置后发布 `ModelConfigChangedEvent`，`ChatClientFactory` 监听事件并重建 `ChatClient` 实例
- 简单、可控、无额外依赖

**备选**：`@RefreshScope` / Redis 配置中心，被否决原因：当前为单模块应用，无需引入分布式配置中心。

### D5：消息日志异步写入：RabbitMQ vs 同步写 MySQL

**选择**：RabbitMQ 异步写入（`chat.message.log` 队列）

**理由**：
- 消息日志写入不应阻塞 SSE 流式响应路径
- 复用现有 RabbitMQ 基础设施（`LlmLogConsumer` 模式）
- 消费者批量写入 MySQL，减少数据库连接开销
- 死信队列保证最终一致性

**备选**：同步写 MySQL，被否决原因：增加 SSE 响应延迟，且高并发下数据库连接池压力大。

### D6：前端 SSE 客户端：`@microsoft/fetch-event-source` vs `EventSource`

**选择**：`@microsoft/fetch-event-source`

**理由**：
- 原生 `EventSource` API 不支持 POST 请求和自定义请求头（无法携带 `Authorization` 和 `X-CSRF-Token`）
- `@microsoft/fetch-event-source` 基于 `fetch` API，支持 POST + 自定义请求头 + 中断控制
- 与现有 axios 拦截器模式兼容（token 注入、401 自动刷新）
- 社区活跃，npm 周下载量 100 万+

**备选**：原生 `EventSource`，被否决原因：无法发送 POST 请求，无法携带 JWT 认证头。

## 模块依赖图

```mermaid
flowchart TB
    subgraph "前端"
        ChatView[ChatView.vue 对话页面]
        ModelConfigView[ModelConfigView.vue 配置管理]
        FetchEventSource[@microsoft/fetch-event-source]
        Marked[marked Markdown 渲染]
    end

    subgraph "接入层"
        ChatController[ChatController]
        ModelConfigController[ModelConfigController]
    end

    subgraph "chat 子域"
        ChatSessionService[ChatSessionService]
        ChatMessageService[ChatMessageService]
        ChatStreamService[ChatStreamService]
        ChatMemory[Spring AI ChatMemory - Redis]
    end

    subgraph "model/config 子域"
        ModelConfigService[ModelConfigService]
        ModelProviderRegistry[ModelProviderRegistry]
        ChatClientFactory[ChatClientFactory 扩展]
        Jasypt[Jasypt 加密]
    end

    subgraph "data 层"
        ChatSessionMapper[ChatSessionMapper]
        ChatMessageMapper[ChatMessageMapper]
        SysModelConfigMapper[SysModelConfigMapper]
        Flyway[Flyway 迁移]
        RabbitMQProducer[ChatMessageLogProducer]
        RabbitMQConsumer[ChatMessageLogConsumer]
    end

    subgraph "中间件"
        MySQL[(MySQL)]
        Redis[(Redis: ChatMemory + Token)]
        RabbitMQ[(RabbitMQ: chat.message.log)]
    end

    ChatView --> FetchEventSource
    ChatView --> Marked
    FetchEventSource --> ChatController
    ChatController --> ChatStreamService
    ChatController --> ChatSessionService
    ChatController --> ChatMessageService
    ChatStreamService --> ChatClientFactory
    ChatStreamService --> ChatMemory
    ChatStreamService --> RabbitMQProducer
    ChatMemory --> Redis
    ChatSessionService --> ChatSessionMapper
    ChatMessageService --> ChatMessageMapper
    ChatSessionMapper --> MySQL
    ChatMessageMapper --> MySQL
    RabbitMQProducer --> RabbitMQ
    RabbitMQ --> RabbitMQConsumer
    RabbitMQConsumer --> ChatMessageMapper

    ModelConfigView --> ModelConfigController
    ModelConfigController --> ModelConfigService
    ModelConfigService --> Jasypt
    ModelConfigService --> SysModelConfigMapper
    ModelConfigService --> ModelProviderRegistry
    ModelProviderRegistry --> ChatClientFactory
    SysModelConfigMapper --> MySQL
```

## 对外暴露接口

| 接口 | 方法 | 路径 | 鉴权 | 用途 |
|------|------|------|------|------|
| 会话列表 | GET | `/api/v1/chat/sessions` | 已认证 | 获取用户会话列表 |
| 创建会话 | POST | `/api/v1/chat/sessions` | 已认证 | 创建新会话 |
| 删除会话 | DELETE | `/api/v1/chat/sessions/{id}` | 已认证 | 删除会话 |
| 消息历史 | GET | `/api/v1/chat/sessions/{id}/messages` | 已认证 | 获取会话消息历史 |
| 发送消息(SSE) | POST | `/api/v1/chat/sessions/{id}/messages/stream` | 已认证 | SSE 流式消息 |
| Token 统计 | GET | `/api/v1/chat/tokens/stats` | 已认证 | 用户 Token 用量统计 |
| 模型配置列表 | GET | `/api/v1/model/configs` | 已认证 | 获取所有模型配置 |
| 创建模型配置 | POST | `/api/v1/model/configs` | 已认证 | 新增模型配置 |
| 更新模型配置 | PUT | `/api/v1/model/configs/{id}` | 已认证 | 更新模型配置 |
| 删除模型配置 | DELETE | `/api/v1/model/configs/{id}` | 已认证 | 删除模型配置 |
| 连接测试 | POST | `/api/v1/model/configs/{id}/test` | 已认证 | 测试 API 连接 |

## 与现有模块的集成点

1. **`AuthUserDetails`**：从 `SecurityContext` 取 `userId` 和 `username`，用于会话归属和消息发送者标识
2. **`ChatClientFactory`**：扩展为支持动态注册/注销 `ChatClient`，监听 `ModelConfigChangedEvent`
3. **`SysModelConfig`**：复用现有实体和表，扩展 `parameters` JSON 字段（模型参数如 temperature、max_tokens）
4. **`LlmLogConsumer` / `RabbitMqConfig`**：复用 RabbitMQ 基础设施，新增 `chat.message.log` 队列和 `ChatMessageLogConsumer`
5. **`BaseDO`**：`ChatSession` 和 `ChatMessage` 继承 `BaseDO`，复用审计字段和逻辑删除
6. **`GlobalExceptionHandler`**：复用统一异常处理，无需新增
7. **`Result<T>`**：所有 Controller 返回 `Result<T>` 统一响应格式
8. **前端 `request.ts`**：axios 拦截器已附加 JWT 和 CSRF Token，对话页面直接复用
9. **前端 `auth` store**：`useAuthStore()` 获取当前用户信息，用于会话归属和 Token 统计

## 数据层结构

### `chat_session` 表 DDL（Flyway `V20260820__create_chat_tables.sql`）

```sql
CREATE TABLE `chat_session` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NOT NULL                COMMENT '用户 ID',
    `title`           VARCHAR(128) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `model_id`        VARCHAR(64)  NOT NULL                COMMENT '使用的模型 ID',
    `context_size`    INT          NOT NULL DEFAULT 10     COMMENT '上下文窗口大小（轮次）',
    `message_count`   INT          NOT NULL DEFAULT 0      COMMENT '消息总数',
    `total_tokens`    BIGINT       NOT NULL DEFAULT 0      COMMENT '累计 Token 用量',
    `creator`         VARCHAR(64)  NOT NULL DEFAULT 'system',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`         VARCHAR(64)  NOT NULL DEFAULT 'system',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_chat_session_user` (`user_id`, `deleted`),
    INDEX `idx_chat_session_update` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';
```

### `chat_message` 表 DDL

```sql
CREATE TABLE `chat_message` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`        BIGINT       NOT NULL                COMMENT '会话 ID',
    `role`              VARCHAR(16)  NOT NULL                COMMENT '角色：user/assistant/system',
    `content`           MEDIUMTEXT   NOT NULL                COMMENT '消息内容',
    `prompt_tokens`     INT          NOT NULL DEFAULT 0      COMMENT '提示词 Token 数',
    `completion_tokens` INT          NOT NULL DEFAULT 0      COMMENT '生成 Token 数',
    `total_tokens`      INT          NOT NULL DEFAULT 0      COMMENT '总 Token 数',
    `latency_ms`        INT          NOT NULL DEFAULT 0      COMMENT '响应延迟（毫秒）',
    `model_id`          VARCHAR(64)  NULL                    COMMENT '使用的模型 ID',
    `creator`           VARCHAR(64)  NOT NULL DEFAULT 'system',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`           VARCHAR(64)  NOT NULL DEFAULT 'system',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_chat_msg_session` (`session_id`, `create_time`),
    INDEX `idx_chat_msg_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';
```

### Redis Key 命名空间（新增）

| Key 模式 | 类型 | TTL | 用途 |
|---------|------|-----|------|
| `chat:ctx:{sessionId}` | List | 24h | Spring AI ChatMemory 对话上下文（由 `RedisChatMemory` 自动管理） |
| `chat:token:{userId}` | String | 永不过期 | 用户 Token 用量配额（HASH 结构：today/week/month/total） |
| `chat:stream:{sessionId}` | String | 60s | 当前活跃的 SSE 流式会话标记（用于防重复发送） |

### RabbitMQ 队列结构（新增）

| Exchange | Queue | Routing Key | Dead Letter | 用途 |
|---------|-------|-------------|-------------|------|
| `chat.exchange`（topic） | `chat.message.log` | `chat.message.log` | `chat.message.log.dlx` | 异步消息日志写入 |

### ES 索引模板（新增）

```json
{
  "index_patterns": ["chat_message_log-*"],
  "template": {
    "mappings": {
      "properties": {
        "sessionId":    { "type": "long" },
        "userId":       { "type": "long" },
        "role":         { "type": "keyword" },
        "content":      { "type": "text", "analyzer": "standard" },
        "modelId":      { "type": "keyword" },
        "totalTokens":  { "type": "integer" },
        "latencyMs":    { "type": "integer" },
        "createTime":   { "type": "date" }
      }
    }
  }
}
```
> 注：ES 索引不作为核心功能依赖，消息日志异步写入 MySQL 为主，ES 索引为后续审计/分析预留，本 change 中仅创建模板不强制写入。

### `sys_model_config` 表扩展 DDL

```sql
ALTER TABLE `sys_model_config`
    ADD COLUMN `parameters` JSON NULL COMMENT '模型参数 JSON（temperature/max_tokens/top_p 等）' AFTER `enabled`,
    ADD COLUMN `api_key_encrypted` VARCHAR(256) NULL COMMENT 'Jasypt 加密后的 API Key' AFTER `api_key`,
    ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重' AFTER `enabled`;
```

## 前端文件结构

```
ai-agent-web/
├── src/
│   ├── api/
│   │   ├── chat.ts                  # 会话/消息/SSE API
│   │   └── model-config.ts          # 模型配置 CRUD + 连接测试 API
│   ├── stores/
│   │   └── chat.ts                   # Pinia 对话 store（会话列表/当前会话/消息列表）
│   ├── views/
│   │   ├── chat/
│   │   │   └── ChatView.vue         # 对话主页（侧边栏 + 主对话区）
│   │   └── model-config/
│   │       └── ModelConfigView.vue  # 模型配置管理页
│   ├── components/
│   │   ├── chat/
│   │   │   ├── SessionList.vue      # 会话列表侧边栏组件
│   │   │   ├── MessageList.vue      # 消息列表组件
│   │   │   ├── MessageInput.vue     # 消息输入框组件
│   │   │   └── MessageBubble.vue    # 消息气泡组件（含 Markdown 渲染）
│   │   └── model-config/
│   │       └── ModelConfigDialog.vue # 模型配置编辑对话框
│   └── router/
│       └── index.ts                  # 添加 /chat 和 /model-configs 路由
```

## 后端包结构

```
com.ai.api.chat/
├── controller/
│   ├── ChatController.java          # 会话 CRUD + 消息历史 + SSE 流式
│   └── ChatTokenController.java     # Token 用量统计
├── service/
│   ├── ChatSessionService.java      # 会话管理
│   ├── ChatMessageService.java      # 消息管理
│   └── ChatStreamService.java       # SSE 流式响应编排
├── entity/
│   ├── ChatSession.java             # 对应 chat_session 表
│   └── ChatMessage.java             # 对应 chat_message 表
├── mapper/
│   ├── ChatSessionMapper.java       # MyBatis-Plus Mapper
│   └── ChatMessageMapper.java
├── dto/
│   ├── CreateSessionReqDTO.java
│   ├── SessionRespDTO.java
│   ├── SendMessageReqDTO.java
│   ├── MessageRespDTO.java
│   └── TokenStatsRespDTO.java
└── mq/
    ├── ChatMessageLogProducer.java   # 消息日志生产者
    └── ChatMessageLogConsumer.java   # 消息日志消费者

com.ai.api.model.config/
├── controller/
│   └── ModelConfigController.java   # 配置 CRUD + 连接测试
├── service/
│   ├── ModelConfigService.java      # 配置管理 + 加密存储
│   └── ModelProviderRegistry.java   # Provider 动态注册表
├── event/
│   └── ModelConfigChangedEvent.java  # 配置变更事件
└── dto/
    ├── ModelConfigReqDTO.java
    ├── ModelConfigRespDTO.java
    └── ModelTestRespDTO.java
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| SSE 长连接占用 Tomcat 线程 | 使用 `Flux<ServerSentEvent>` + Servlet 3.1 异步模式，不阻塞工作线程；配置 Tomcat 最大连接数 10000 |
| `ChatClientFactory` 动态重建 `ChatClient` 时，已存在的 `Flux` 流可能持有旧实例 | 重建时使用 `AtomicReference` 无锁切换，旧 `ChatClient` 等待当前流完成后被 GC |
| Jasypt 主密钥泄漏后所有 API Key 可被解密 | 主密钥通过环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 注入，不入仓库；定期轮换机制在 `add-key-rotation` change |
| 上下文窗口过大导致 Token 消耗过高 | 默认窗口 10 轮，可通过 `ai.chat.context-window-size` 配置调整；前端显示 Token 用量 |
| `@microsoft/fetch-event-source` 在部分浏览器兼容性问题 | 该库基于 `fetch` API，支持 Chrome/Firefox/Safari/Edge 主流浏览器；IE 不支持（已 EOL） |
| 模型配置变更后，正在进行的 SSE 流可能中断 | 配置变更时先发布 `ModelConfigChangedEvent`，`ChatStreamService` 在下一次请求时使用新配置；当前流不受影响 |

## Migration Plan

1. **准备阶段**：
   - 执行 Flyway 迁移脚本 `V20260820__create_chat_tables.sql` 创建 `chat_session` 和 `chat_message` 表
   - 执行 Flyway 迁移脚本 `V20260821__extend_model_config.sql` 扩展 `sys_model_config` 表
   - 引入 `jasypt-spring-boot-starter` 和 `spring-boot-starter-webflux` 依赖
   - 前端安装 `@microsoft/fetch-event-source`
2. **后端部署**：
   - 部署新版本，Flyway 自动执行建表
   - `ChatClientFactory` 扩展为从 `ModelProviderRegistry` 动态获取配置
   - 现有 `ModelPingController` 不受影响
3. **前端部署**：
   - 新增 `/chat` 和 `/model-configs` 路由
   - 侧边栏"对话聊天"菜单项由 disabled 变为可用
4. **回滚策略**：
   - 数据库：`V20260822__rollback_chat_tables.sql`（DROP TABLE chat_message, chat_session）
   - 前端：回退到上一个 tag
   - 后端：`ChatClientFactory` 回退到硬编码配置模式

## Open Questions

（无 — 关键决策已在上文确定，`SystemPromptService` 系统提示词管理留待 `add-prompt-management` change）