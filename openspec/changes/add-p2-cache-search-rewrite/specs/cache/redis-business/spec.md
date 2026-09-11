## Purpose

为对话历史、RAG 检索结果等高频读场景提供 Redis 业务缓存，并通过 Redisson 分布式锁覆盖并发写场景，降低 MySQL / ES 重复负载、避免并发数据冲突。

## ADDED Requirements

### Requirement: 会话历史 Redis 缓存

系统 SHALL 在读取会话消息列表时优先查询 Redis Key `chat:history:{sessionId}`（TTL 1h），未命中时回源 MySQL 并回填缓存。

#### Scenario: 缓存命中返回会话历史

- **WHEN** 调用 `getMessages(sessionId)` 且 `chat:history:{sessionId}` 存在
- **THEN** 直接反序列化 Redis Value 返回消息列表
- **AND** 不触发 MySQL 查询，响应时长 < 50ms

#### Scenario: 缓存未命中回源并回填

- **WHEN** 调用 `getMessages(sessionId)` 且 Redis Key 不存在
- **THEN** 查询 MySQL 获取消息列表
- **AND** 将结果序列化写入 Redis，TTL 设为 1h
- **AND** 返回消息列表

#### Scenario: 新消息写入时失效缓存

- **WHEN** `ChatMessageLogConsumer` 成功写入新消息
- **THEN** 删除 `chat:history:{sessionId}` 缓存
- **AND** 下次读取触发回源回填，保证一致性

#### Scenario: 异常场景降级直查 MySQL

- **WHEN** Redis 不可达或反序列化失败
- **THEN** 记录 WARN 日志后直查 MySQL 返回
- **AND** 不向调用方抛出异常，保证主流程可用

### Requirement: RAG 检索结果缓存

系统 SHALL 对 RAG 检索结果按 `query_hash`（SHA-256 规范化 query）做缓存 `rag:hits:{query_hash}`（TTL 5min），相同 query 命中缓存时短路向量检索。

#### Scenario: 相同 query 命中缓存

- **WHEN** 用户提交 query 且 `rag:hits:{query_hash}` 存在
- **THEN** 直接返回缓存的 chunks 列表
- **AND** 不触发 ES 向量检索，响应时长 < 20ms

#### Scenario: query_hash 未命中走完整检索

- **WHEN** 用户提交 query 且缓存未命中
- **THEN** 执行向量检索 + 重排序，返回结果
- **AND** 异步写入 `rag:hits:{query_hash}`，TTL 5min

#### Scenario: 知识库内容更新时清理缓存

- **WHEN** 知识库新增 / 删除文档或重新向量化
- **THEN** 按 `kbId` 模式删除 `rag:hits:kb:{kbId}:*` 缓存
- **AND** 避免脏数据影响后续检索

### Requirement: Redisson 分布式锁

系统 SHALL 通过 Redisson 提供分布式锁，覆盖「知识库批量导入串行化」「模型配置更新互斥」场景。

#### Scenario: 知识库批量导入串行化

- **WHEN** 同一知识库 ID 的批量导入并发触发
- **THEN** 仅持有 `lock:kb:import:{kbId}` 锁的线程执行导入
- **AND** 其他线程等待或返回「正在导入中，请稍后」
- **AND** 锁 TTL 30s，防止持锁线程异常导致死锁

#### Scenario: 模型配置更新互斥

- **WHEN** 管理员更新模型配置时另一更新并发触发
- **THEN** 持有 `lock:model:config:{modelId}` 锁的线程执行更新
- **AND** 另一线程返回「配置更新中，请稍后重试」，避免配置覆盖

#### Scenario: 持锁线程异常自动释放

- **WHEN** 持锁线程抛出异常未显式释放锁
- **THEN** Redisson watchdog 在 TTL 到期后自动释放
- **AND** 后续等待线程可获取锁
