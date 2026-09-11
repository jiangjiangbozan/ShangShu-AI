## Why

P0/P1 阶段已修复对话历史展示、RAG 文档管理、消息队列可靠性等基础缺陷，但当前平台仍存在 4 个核心价值瓶颈：

1. **MySQL 重复查询压力**：会话历史高频访问直连 MySQL，热点 RAG 查询每次都走 ES 向量检索，响应耗时与底层负载双高。
2. **对话历史孤岛**：消息仅存 MySQL，无法跨会话全局检索，用户无法回溯历史问答。
3. **RAG 召回偏差**：用户口语化长问句导致向量检索信息稀释，内部文档术语密集场景下召回率偏低。
4. **MQ 序列化隐患**：Producer 仍存在手写 `escapeJson` 字符串拼接逻辑（P1-4 仅修复了 Producer 之一，Consumer 与其他链路仍有手写拼接），emoji / 控制字符场景消息消费失败产生脏数据。

本 change（P2 中期落地，1 个月内）一次性解决上述 4 项瓶颈，提升核心价值与稳定性。

## What Changes

### 1. Redis 业务缓存与分布式锁体系
- 新增 `chat:history:{sessionId}` 缓存（TTL 1h）：会话历史优先从 Redis 返回，未命中回源 MySQL 并回填。
- 新增 `rag:hits:{query_hash}` 缓存（TTL 5min）：相同 query 跳过向量检索直接返回结果，降低 ES 压力。
- 引入 Redisson 分布式锁：覆盖「知识库批量导入串行化」「模型配置更新互斥」两个并发场景。

### 2. 对话历史 ES 双写与全局检索
- `ChatMessageLogConsumer` 写 MySQL 成功后异步双写到 ES `chat_message_log` 索引。
- 新增 `GET /api/v1/chat/search?q=xxx` 接口，支持跨会话关键词检索。
- 前端对话页新增全局搜索入口，结果可点击跳回原会话上下文。

### 3. RAG Query 重写优化
- 新增 `QueryRewriteService`：调用轻量大模型将用户原始 query 改写为 2-3 个语义等价的检索问句。
- 多路并行召回后通过 RRF（Reciprocal Rank Fusion）融合结果。
- 适配长问句信息稀释、口语化表述场景，尤其针对内部文档术语密集场景。

### 4. MQ 序列化规范升级
- 用 Jackson `ObjectMapper` 标准序列化替换所有手写 `escapeJson` 字符串拼接逻辑。
- 覆盖 `ChatMessageLogProducer`、`ChatMessageLogConsumer` payload 解析、以及其他 MQ 链路。
- 彻底解决 emoji / 特殊控制字符序列化异常，避免消费失败与脏数据。

## Capabilities

### New Capabilities
- `cache/redis-business`: Redis 业务缓存（会话历史 / RAG 命中）与 Redisson 分布式锁体系
- `chat/search`: 对话历史 ES 双写与跨会话全局检索能力
- `rag/query-rewrite`: RAG 查询重写与 RRF 多路召回融合
- `mq/serialization`: RabbitMQ 消息序列化规范（Jackson 统一）

### Modified Capabilities
- `chat/message-log`: 消费者写入 MySQL 后增加 ES 异步双写分支
- `rag/retrieval`: 检索主流程前置 Query 重写步骤，命中缓存时短路

## Impact

- **后端代码**：
  - `api/src/main/java/com/ai/api/chat/` 新增 `cache/ChatHistoryCacheService.java`、`search/ChatSearchService.java`、`search/ChatSearchController.java`
  - `api/src/main/java/com/ai/api/knowledge/` 新增 `cache/RagHitsCacheService.java`、`rewrite/QueryRewriteService.java`
  - `api/src/main/java/com/ai/api/chat/mq/` 升级 `ChatMessageLogProducer.java`、`ChatMessageLogConsumer.java`、引入 `JacksonMessageConverter`
  - `api/src/main/java/com/ai/api/common/lock/` 新增 `DistributedLockService.java`（Redisson）
  - `api/src/main/java/com/ai/api/data/config/` 新增 `RedissonConfig.java`、`JacksonMqConfig.java`
- **前端代码**：
  - `ai-agent-web/src/views/chat/` 新增全局搜索组件 `GlobalSearch.vue`
  - `ai-agent-web/src/api/chat.ts` 新增 `searchMessages` 接口
- **数据库**：无表结构变更（ES 新增索引 `chat_message_log`）
- **依赖**：`api/pom.xml` 新增 `redisson-spring-boot-starter`
- **配置**：`application-dev.yml` 新增 Redisson、ES 索引、缓存 TTL 配置
- **关联 change**：依赖前序 change `add-chat-module`（已落地）

## 非目标

- 不引入多级缓存（Caffeine 本地缓存）—— 留待 V2 评估
- 不实现 RAG 语义缓存（query 语义相似度匹配）—— 本期仅做 query_hash 精确匹配
- 不改造 RabbitMQ 死信与幂等机制 —— P1 已落地，本期仅升级序列化方式
- 不做对话历史 ES 全文检索的高级分词调优 —— 用默认 IK 分词器
- 不实现 Query 重写的 A/B 测试对比框架 —— 留待 P3 评测体系

## 验收标准

1. 会话历史二次访问命中率 ≥ 60%，平均响应时长较直连 MySQL 下降 ≥ 50%
2. 相同 query 5 分钟内重复访问，RAG 检索耗时下降 ≥ 70%，ES QPS 下降可见
3. `GET /api/v1/chat/search?q=差旅` 能跨会话返回包含关键词的消息列表，支持会话跳转
4. Query 重写后 RAG 召回率（recall@5）较原始 query 提升 ≥ 15%（内部文档测试集）
5. 含 emoji 与控制字符的聊天消息通过 MQ 链路完整落库，无消费失败
6. 知识库批量导入并发场景下，Redisson 锁成功串行化，无重复入库
