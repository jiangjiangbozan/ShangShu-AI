# 实施任务 - add-p2-cache-search-rewrite

> 任务粒度 ≤ 4 小时；按数据层 → 能力层 → 业务层 → 接入层顺序执行；每个任务标注验证方式。

## 阶段 1: 依赖与配置（数据层准备）

### Task 1.1: 引入 Redisson 依赖与配置

- [x] 在 `api/pom.xml` 新增 `redisson-spring-boot-starter` 依赖（版本 16.x，对齐 Spring Boot 4.1）
- [x] 在 `api/src/main/java/com/ai/api/data/config/` 新增 `RedissonConfig.java`，从 `application-dev.yml` 读取单 server 配置
- [x] `application-dev.yml` 新增 `redisson.config` 配置块，密码走环境变量
- **验证**：`mvn compile` 通过；启动应用确认 Redisson 连接 Redis 成功；`RLock` 单元测试可获取与释放

### Task 1.2: 配置 Jackson MQ MessageConverter

- [x] 在 `api/src/main/java/com/ai/api/data/config/` 新增 `JacksonMqConfig.java`，注册 `Jackson2JsonMessageConverter` Bean
- [x] 验证现有 `RabbitTemplate` 与 `@RabbitListener` 默认使用该 Converter
- [x] 兼容现有手写 JSON 格式消息（Jackson 反序列化标准 JSON 不报错）
- **验证**：发送一条含 emoji 测试消息，Consumer 成功消费无异常；历史消息兼容测试通过

### Task 1.3: 创建 ES chat_message_log 索引与初始化

- [x] 在 `api/src/main/java/com/ai/api/chat/search/` 新增 `ChatSearchIndexInitializer.java`，应用启动时若索引不存在则创建
- [x] mapping 按 design.md 定义（content 走 ik_smart 分词）
- [x] 索引名通过 `application-dev.yml` `elasticsearch.indices.chat-message-log` 配置
- **验证**：删除索引后重启应用，索引自动创建；`GET chat_message_log/_mapping` 字段正确

## 阶段 2: 能力层（Redis 缓存 + 分布式锁）

### Task 2.1: 实现 ChatHistoryCacheService

- [x] 在 `api/src/main/java/com/ai/api/chat/cache/` 新增 `ChatHistoryCacheService.java`
- [x] 实现 `get(sessionId)`：先查 Redis `chat:history:{sessionId}`，未命中返回 null
- [x] 实现 `getOrLoad(sessionId, loader)`：未命中时调用 loader 回源并回填
- [x] 实现 `evict(sessionId)`：删除 Redis Key
- [x] Redis 异常时降级返回 null，不抛出
- [x] 序列化使用 `Jackson2JsonRedisSerializer`
- **验证**：单测覆盖命中 / 未命中 / 异常降级三场景

### Task 2.2: 实现 RagHitsCacheService

- [x] 在 `api/src/main/java/com/ai/api/knowledge/cache/` 新增 `RagHitsCacheService.java`
- [x] 实现 `get(kbId, queryHash)` 与 `put(kbId, queryHash, chunks)`，TTL 5min
- [x] 实现 `evictByKb(kbId)`：按 `rag:hits:kb:{kbId}:*` 模式删除（使用 `SCAN` 避免阻塞）
- [x] `queryHash` = SHA-256(normalize(query))，normalize 去除前后空格 + 转小写
- **验证**：单测覆盖缓存命中 / 模式删除 / TTL 过期三场景

### Task 2.3: 实现 DistributedLockService

- [x] 在 `api/src/main/java/com/ai/api/common/lock/` 新增 `DistributedLockService.java`
- [x] 实现 `tryLock(lockKey, ttlSeconds, supplier)`：获取锁后执行 supplier，失败返回 Optional.empty
- [x] 实现 `tryLockOrElse(lockKey, ttlSeconds, supplier, fallback)`：获取失败时执行 fallback
- [x] watchdog 自动续期，无需手动续
- **验证**：并发 10 线程测试 `tryLock`，仅 1 线程获取锁；锁释放后等待线程可获取

## 阶段 3: 能力层（ES 双写 + Query 重写）

### Task 3.1: 实现 ChatMessageLogConsumer 双写逻辑

- [x] 修改 `ChatMessageLogConsumer.handleMessageLog`，写 MySQL 成功后向 `chat.search.index` 队列投递索引任务
- [x] 索引任务 payload：`{messageId, sessionId, userId, role, content, modelId, createTime}`
- [x] 双写失败仅记录 WARN，不影响主流程 ack
- **验证**：发送一条消息，MySQL 写入成功后 `chat.search.index` 队列收到消息

### Task 3.2: 实现 ChatSearchIndexConsumer

- [x] 在 `api/src/main/java/com/ai/api/chat/search/` 新增 `ChatSearchIndexConsumer.java`
- [x] 监听 `chat.search.index` 队列，手动 ack + 死信队列
- [x] 调用 `ElasticsearchClient.index()` 写入 `chat_message_log` 索引
- [x] 索引失败 nack 入死信队列
- **验证**：队列消息消费后 ES `chat_message_log` 能查到对应文档

### Task 3.3: 实现 QueryRewriteService

- [x] 在 `api/src/main/java/com/ai/api/knowledge/rewrite/` 新增 `QueryRewriteService.java`
- [x] 实现 `rewrite(query)`：调用 DeepSeek，使用 design.md 中 Prompt 模板，`max_tokens=200, temperature=0.3, timeout=3s`
- [x] query 长度 < 6 字符直接返回 `[query]`
- [x] LLM 调用失败 fallback 到 Ollama Qwen2.5，仍失败返回 `[query]`
- [x] 解析 LLM 输出的 JSON 数组，截断到前 3 条
- **验证**：单测覆盖正常重写 / 短 query 跳过 / LLM 失败降级三场景

### Task 3.4: 实现 RRF 多路召回融合

- [x] 在 `api/src/main/java/com/ai/api/knowledge/rewrite/` 新增 `RrfFusionService.java`
- [x] 实现 `fuse(multiResults, k=60)`：按 RRF 公式计算融合分，去重后排序取 topK
- [x] 同一 chunk 在多路出现时分数累加
- [x] 单路失败容错，贡献为 0
- **验证**：单测覆盖 3 路并行 + 1 路失败容错场景

### Task 3.5: 集成 RAG 主流程（ChatStreamService）

- [x] 修改 `ChatStreamService` 构建 `QuestionAnswerAdvisor` 处，前置调用 `QueryRewriteService.rewrite`
- [x] 多路 query 并行检索 + RRF 融合后传给 `QuestionAnswerAdvisor`（或直接调用 vectorStore 多次 search）
- [x] 命中 `RagHitsCacheService` 时短路整个流程
- [x] 写入知识库时调用 `ragHitsCacheService.evictByKb(kbId)`
- **验证**：长问句场景 RAG 召回率提升（对比基线）；相同 query 5min 内重复访问 ES 查询次数减少

## 阶段 4: 业务层与接入层

### Task 4.1: 集成 ChatHistoryCacheService 到 ChatMessageService

- [x] 修改 `ChatMessageService.getMessages` 包装层：调用 `chatHistoryCacheService.getOrLoad`
- [x] 修改 `ChatMessageLogConsumer.handleMessageLog`：写 MySQL 成功后调用 `evict(sessionId)`
- **验证**：第一次查询响应 100ms+（回源），第二次 < 50ms（命中）；新消息写入后下次查询得到新消息

### Task 4.2: 集成 DistributedLockService 到批量导入

- [x] 修改 `KnowledgeBaseService.batchImport` 入口：`distributedLockService.tryLockOrElse("lock:kb:import:" + kbId, 30, supplier, () -> throw 409)`
- [x] 并发时返回 409 状态码与「正在导入中，请稍后」提示
- [x] 同步更新 Controller 异常处理
- **验证**：并发 2 个相同 kbId 批量导入请求，仅 1 个成功，另 1 个返回 409

### Task 4.3: 实现 ChatSearchService 与 Controller

- [x] 在 `api/src/main/java/com/ai/api/chat/search/` 新增 `ChatSearchService.java`，调用 ES `ElasticsearchClient.search`
- [x] 实现 `search(userId, q, page, size)`：按 `userId` 过滤 + `content` match 查询 + 按时间倒序 + 关键词高亮
- [x] 新增 `ChatSearchController.java`，`GET /api/v1/chat/search`，参数 `q` / `page` / `size`
- [x] 未登录返回 401
- **验证**：插入测试消息到 ES，调用接口返回匹配结果；空关键词返回空列表

### Task 4.4: MQ Producer / Consumer 手写拼接清理

- [x] 全局搜索 `escapeJson` / `String.format(...,` 拼接 JSON 的代码，替换为 `objectMapper.writeValueAsBytes`
- [x] 全局搜索 `payload.split` / `substring` 解析 JSON 的代码，替换为 `objectMapper.readTree` 或泛型反序列化
- [x] 移除已废弃的手写工具方法
- **验证**：编译通过；发送含 emoji / 控制字符消息，Consumer 完整还原无脏数据

## 阶段 5: 前端配套

### Task 5.1: 前端新增全局搜索 API

- [x] 在 `ai-agent-web/src/api/chat.ts` 新增 `searchMessages(q: string, page: number, size: number)`
- [x] 定义 `ChatSearchResultResp` TypeScript 接口
- **验证**：`pnpm type-check` 通过

### Task 5.2: 前端新增 GlobalSearch 组件

- [x] 在 `ai-agent-web/src/components/chat/` 新增 `GlobalSearch.vue`
- [x] 顶部搜索框 + 防抖 350ms + 结果面板（按时间倒序）
- [x] 每条结果显示 `sessionTitle` / `role` 标签 / `content` 高亮 / `createTime`
- [x] 点击结果调用 `chatStore.selectSession(sessionId)` 跳转
- **验证**：搜索「差旅」能返回结果列表；点击跳转到对应会话

### Task 5.3: 集成 GlobalSearch 到 ChatView

- [x] 在 `ai-agent-web/src/views/chat/ChatView.vue` 顶部导航栏引入 `GlobalSearch` 组件
- [x] 跳转后通过 `messageId` 滚动到对应消息位置
- **验证**：对话页能打开搜索框，搜索 - 跳转 - 滚动流程闭环

## 阶段 6: 验证与文档

### Task 6.1: 端到端验证

- [x] 场景 1：会话历史第二次访问 < 50ms（缓存命中）
- [x] 场景 2：相同 query 5min 内重复访问，ES QPS 可视化下降
- [x] 场景 3：`/api/v1/chat/search?q=差旅` 返回跨会话结果
- [x] 场景 4：长问句 RAG 召回率对比基线提升 ≥ 15%
- [x] 场景 5：含 emoji 消息通过 MQ 完整落库
- [x] 场景 6：并发批量导入返回 409

### Task 6.2: 更新文档

- [x] 更新 `docs/architecture.md` 新增 Redis 缓存、Redisson 锁、ES 双写、Query 重写章节
- [x] 更新 `docs/api/` OpenAPI 描述新增 `/api/v1/chat/search` 接口
- [x] 追加 `docs/dev-log/2026-08-20.md`（或当日日志）记录本期落地过程
- [x] 同步 `project_memory.md` 沉淀关键知识点

## 阶段 7: 归档准备

### Task 7.1: OpenSpec validate

- [x] 运行 `openspec validate add-p2-cache-search-rewrite` 确认工件完整
- [x] 修复 lint 错误，确认 status 全部 done

### Task 7.2: archive 前置确认

- [x] 确认 `tasks.md` 全部勾选
- [x] 准备归档摘要（关键文件路径列表）
- **验证**：准备进入 `openspec archive` 流程
