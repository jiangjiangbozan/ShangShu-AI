## Purpose

基础记忆模块实现短期滑动窗口 + Token 压缩，避免长对话 token 爆炸；定义 MemoryStore 接口预留长期记忆扩展。

## ADDED Requirements

### Requirement: 短期记忆滑动窗口

`ShortTermMemoryStore` SHALL 维护每个 runtime 的对话历史，按 `agent.memory.window-size=10` 配置保留最近 N 轮原始消息，超出的旧消息 MUST 触发压缩。

#### Scenario: 窗口内消息直接返回

- **GIVEN** runtime 123 当前有 5 轮消息
- **WHEN** 调用 `memoryStore.load(123)`
- **THEN** 返回全部 5 轮消息原文，不触发压缩

#### Scenario: 超窗口触发压缩

- **GIVEN** runtime 123 当前有 12 轮消息，窗口配置 10
- **WHEN** 第 13 轮调用前 `memoryStore.load(123)`
- **THEN** 触发压缩：保留最近 2 轮原文，前 11 轮调 LLM 摘要
- **AND** 压缩结果写入 `agent:ctx:{runtimeId}` Redis Key（TTL 24h）
- **AND** 返回 `[summary, msg11, msg12]` 三条消息供下一轮 LLM 使用

### Requirement: Token 阈值压缩

即使消息数未超窗口，单次 LLM 调用 prompt token 超 `agent.memory.token-threshold=4096` 时 MUST 触发压缩。

#### Scenario: token 阈值压缩

- **WHEN** LLM 调用前估算 prompt token = 5000
- **THEN** 触发压缩，调用 LLM 摘要历史，保留最近 2 轮原文 + 历史摘要
- **AND** 压缩后 prompt token ≤ 4096（实测值，否则递归压缩前 N-1 轮）

#### Scenario: token 估算与实际偏差

- **WHEN** 估算 3000 token 但实际 6000 token，LLM 返回 413 Payload Too Large
- **THEN** 捕获异常，强制压缩到最近 2 轮 + 1 条摘要，重试一次
- **AND** 重试仍失败则抛 `BusinessException(ErrorCode.MEMORY_COMPRESS_FAILED)`

### Requirement: 压缩快照持久化

压缩后的快照 MUST 同时写 MySQL `agent_runtime.context_snapshot` 与 Redis `agent:ctx:{runtimeId}`。

#### Scenario: 双写一致性

- **WHEN** 压缩完成
- **THEN** Redis 写入成功（TTL 24h），返回前端
- **AND** 异步更新 MySQL `context_snapshot` 字段（不阻塞主流程）
- **AND** Redis 写失败时降级直读 MySQL，不抛错

### Requirement: MemoryStore 接口预留

系统 SHALL 定义 `MemoryStore` 接口供长期记忆扩展，本 change 仅实现 `ShortTermMemoryStore`，长期实现留 V2。

```java
public interface MemoryStore {
  List<Message> load(Long runtimeId);
  void save(Long runtimeId, List<Message> messages);
  void compress(Long runtimeId);
  // 长期记忆接口（V2 实现）
  void saveLongTerm(Long userId, String key, Object value);
  Object recallLongTerm(Long userId, String query);
}
```

#### Scenario: ShortTermMemoryStore 实现

- **WHEN** `MemoryStore bean` 注入到 ReactEngine
- **THEN** 实际类型为 `ShortTermMemoryStore`
- **AND** `saveLongTerm` / `recallLongTerm` 抛 `UnsupportedOperationException("long-term memory not supported in M4")`

#### Scenario: 长期记忆接口未实现

- **WHEN** ReactEngine 调用 `memoryStore.recallLongTerm(userId, "user-preference")`
- **THEN** 抛 `UnsupportedOperationException`
- **AND** ReactEngine 捕获后降级为"无长期记忆"，不中断流程
