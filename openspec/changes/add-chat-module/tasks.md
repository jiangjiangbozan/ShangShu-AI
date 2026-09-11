## 1. 基础设施准备

- [x] 1.1 创建 Flyway 迁移脚本 [V20260820__create_chat_tables.sql](file:///d:/ai/api/src/main/resources/db/migration/V20260820__create_chat_tables.sql)，按 design.md DDL 创建 `chat_session` 和 `chat_message` 表
  - 验证：应用启动时 Flyway 自动执行，两张表存在且索引正确
- [x] 1.2 创建 Flyway 迁移脚本 [V20260821__extend_model_config.sql](file:///d:/ai/api/src/main/resources/db/migration/V20260821__extend_model_config.sql)，扩展 `sys_model_config` 表（新增 `parameters` JSON、`api_key_encrypted`、`sort_order` 字段）
  - 验证：`sys_model_config` 表结构包含新字段
- [x] 1.3 修改 [pom.xml](file:///d:/ai/api/pom.xml)：新增 `jasypt-spring-boot-starter` 3.0.5（配置加密）、`spring-boot-starter-webflux`（仅用于 `Flux` 类型支持 SSE）
  - 验证：`./mvnw.cmd dependency:resolve` 成功解析新依赖
- [x] 1.4 修改 [application-dev.yml](file:///d:/ai/api/src/main/resources/application-dev.yml)：新增 `chat.*` 配置段（context-window-size=10、stream-timeout=PT5M、max-tokens-per-day=100000）、`jasypt.encryptor.password` 配置
  - 验证：配置类 `ChatProperties` 能正确绑定
- [x] 1.5 创建 ES 索引模板 JSON 文件 [chat-message-log-template.json](file:///d:/ai/api/src/main/resources/es/chat-message-log-template.json)，按 design.md 定义
  - 验证：`curl -X PUT localhost:9200/_index_template/chat_message_log` 创建成功
- [x] 1.6 创建 RabbitMQ 配置：在 [RabbitMqConfig.java](file:///d:/ai/api/src/main/java/com/ai/api/data/config/RabbitMqConfig.java) 中新增 `chat.exchange`、`chat.message.log` 队列、`chat.message.log.dlx` 死信队列
  - 验证：应用启动后 RabbitMQ 管理界面可见新增队列

## 2. 对话数据层

- [x] 2.1 创建实体 [ChatSession.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/entity/ChatSession.java)，继承 `BaseDO`，添加 `@TableName("chat_session")`
  - 验证：编译通过，字段与表一一对应
- [x] 2.2 创建实体 [ChatMessage.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/entity/ChatMessage.java)，继承 `BaseDO`，添加 `@TableName("chat_message")`
  - 验证：编译通过
- [x] 2.3 创建 [ChatSessionMapper.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/mapper/ChatSessionMapper.java)，继承 `BaseMapper<ChatSession>`，声明 `selectByUserId` 方法
  - 验证：单测 `ChatSessionMapperTest` 覆盖 CRUD + 按用户查询
- [x] 2.4 创建 [ChatSessionMapper.xml](file:///d:/ai/api/src/main/resources/mapper/chat/ChatSessionMapper.xml)，实现自定义查询
  - 验证：单测通过
- [x] 2.5 创建 [ChatMessageMapper.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/mapper/ChatMessageMapper.java)，继承 `BaseMapper<ChatMessage>`，声明 `selectBySessionId` 方法
  - 验证：单测 `ChatMessageMapperTest` 覆盖 CRUD + 按会话查询
- [x] 2.6 创建 [ChatMessageMapper.xml](file:///d:/ai/api/src/main/resources/mapper/chat/ChatMessageMapper.xml)，实现自定义查询
  - 验证：单测通过

## 3. 对话服务层

- [x] 3.1 创建 [ChatProperties.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/config/ChatProperties.java)，`@ConfigurationProperties("ai.chat")` 绑定 context-window-size、stream-timeout、max-tokens-per-day
  - 验证：单测 `ChatPropertiesTest` 验证默认值与绑定值
- [x] 3.2 创建 [ChatSessionService.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/service/ChatSessionService.java)：实现 `createSession`、`listSessions`、`deleteSession`、`getSession` 方法
  - 验证：单测 `ChatSessionServiceTest`（Mock Mapper），覆盖创建/查询/删除/权限校验
- [x] 3.3 创建 [ChatMessageService.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/service/ChatMessageService.java)：实现 `saveMessage`、`getMessagesBySession`、`getRecentMessages`（上下文窗口截断）
  - 验证：单测 `ChatMessageServiceTest`，覆盖保存/查询/窗口截断
- [x] 3.4 创建 [ChatStreamService.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/service/ChatStreamService.java)：实现 SSE 流式响应编排
  - 核心逻辑：从 `ModelProviderRegistry` 获取 `ChatClient` → 组装 `ChatMemory` 上下文 → 调用 `chatClient.prompt().user(content).advisors(chatMemoryAdvisor).stream().content()` → 返回 `Flux<ServerSentEvent<String>>`
  - Token 统计：从 `ChatResponse` 的 `Usage` 提取，消息完成后异步写入 `chat.message.log` 队列
  - 验证：单测 `ChatStreamServiceTest`（Mock ChatClient + ChatMemory），覆盖流式响应/完成事件/中断处理
- [x] 3.5 创建 [ChatMessageLogProducer.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/mq/ChatMessageLogProducer.java)：RabbitMQ 生产者，发送消息日志到 `chat.message.log` 队列
  - 验证：单测 `ChatMessageLogProducerTest`（Mock RabbitTemplate）
- [x] 3.6 创建 [ChatMessageLogConsumer.java](file:///d:/ai/api/src/main/java/com/ai/api/chat/mq/ChatMessageLogConsumer.java)：RabbitMQ 消费者，异步写入消息日志到 MySQL
  - 验证：单测 `ChatMessageLogConsumerTest`（Mock Mapper）

## 4. 对话接入层

- [x] 4.1 创建 DTO
- [x] 4.2 创建 ChatController
- [x] 4.3 创建 ChatTokenController

## 5. 模型配置服务层

- [x] 5.1 创建 ModelConfigService
- [x] 5.2 创建 ModelProviderRegistry
- [x] 5.3 创建 ModelConfigChangedEvent
- [x] 5.4 修改 ChatClientFactory

## 6. 模型配置接入层

- [x] 6.1 创建 DTO：ModelConfigReqDTO、ModelConfigRespDTO、ModelTestRespDTO
- [x] 6.2 创建 ModelConfigController

## 7. 前端基础设施

- [x] 7.1 @microsoft/fetch-event-source 已存在
- [x] 7.2 创建 src/api/chat.ts
- [x] 7.3 创建 src/api/model-config.ts
- [x] 7.4 创建 src/stores/chat.ts

## 8. 前端页面

- [x] 8.1 创建 SessionList.vue
- [x] 8.2 创建 MessageBubble.vue
- [x] 8.3 创建 MessageList.vue
- [x] 8.4 创建 MessageInput.vue
- [x] 8.5 创建 ChatView.vue
- [x] 8.6 创建 ModelConfigDialog.vue
- [x] 8.7 创建 ModelConfigView.vue
- [x] 8.8 修改 router/index.ts（添加 /chat 和 /model-configs 路由）
- [x] 8.9 修改 DefaultLayout.vue（启用对话聊天和模型配置菜单项）

## 9. 集成与验证

- [x] 9.1 后端编译通过（./mvnw.cmd compile）
- [x] 9.2 前端 type-check 通过（npm run type-check）
- [ ] 9.3 端到端联调（需本地基础设施运行）
- [ ] 9.4 性能验证（需本地基础设施运行）
- [ ] 9.5 配置动态加载验证（需本地基础设施运行）

## 10. 文档

- [x] 10.1 创建 [docs/api/chat.md](file:///d:/ai/docs/api/chat.md)
- [x] 10.2 创建 [docs/api/model-config.md](file:///d:/ai/docs/api/model-config.md)
- [x] 10.3 创建 [docs/infrastructure-setup.md](file:///d:/ai/docs/infrastructure-setup.md)
- [x] 10.4 更新 [docs/changes-roadmap.md](file:///d:/ai/docs/changes-roadmap.md) — 标注 `add-chat-module` 完成