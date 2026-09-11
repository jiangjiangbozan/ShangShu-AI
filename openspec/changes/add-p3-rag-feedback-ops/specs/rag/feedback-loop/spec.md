## Purpose

通过检索命中计数、点踩反馈、定时重处理形成「检索-反馈-优化」自闭环，持续提升知识库召回质量与内容运营效率。

## ADDED Requirements

### Requirement: 命中计数字段

`knowledge_chunk` 表 SHALL 新增 `hit_count` 字段，记录该分片累计被检索命中次数。

#### Scenario: 命中计数异步累加

- **WHEN** 检索召回返回的 chunk 列表中包含 chunkId
- **THEN** 异步调用 `chunkFeedbackService.incrHit(chunkId)` 使 `hit_count` +1
- **AND** 不阻塞主检索流程，计数失败仅记录 WARN 日志

#### Scenario: 字段初始化与默认值

- **WHEN** Flyway 执行迁移脚本
- **THEN** `ALTER TABLE knowledge_chunk ADD COLUMN hit_count INT NOT NULL DEFAULT 0`
- **AND** 既有分片 `hit_count` 默认为 0

### Requirement: 点踩反馈减分

用户点踩某 chunk 反馈后 SHALL 使 `hit_count` -3，触发该 chunk 进入低质列表候选。

#### Scenario: 用户点踩成功

- **WHEN** 用户在前端对某回答关联的 chunk 提交点踩
- **THEN** `chunkFeedbackService.decrHit(chunkId)` 使 `hit_count` -3（下限为 0）
- **AND** 记录 `chunk_feedback_log` 表（userId / chunkId / feedbackType=negative / createTime）

#### Scenario: 重复点踩幂等

- **WHEN** 同一用户对同一 chunk 重复点踩
- **THEN** 不重复减分
- **AND** 返回「已反馈过」提示

### Requirement: 低质分片定时重处理

系统 SHALL 提供定时任务，每周日凌晨触发低质分片（`hit_count < 5` 且 `score < 0.3`）的重新解析与向量化。

#### Scenario: 定时任务触发

- **WHEN** 每周日 02:00（cron `0 0 2 * * 0`）
- **THEN** 扫描所有 `hit_count < 5` 且 `score < 0.3` 的 chunk
- **AND** 对每个 chunk 触发重新解析（重新切分 / 重新向量化）
- **AND** 重处理完成后 `hit_count` 重置为 0，记录 `reprocess_log` 表

#### Scenario: 重处理失败重试

- **WHEN** 单个 chunk 重处理失败（如 LLM 调用超时）
- **THEN** 重试 3 次后跳过，记录 ERROR 日志
- **AND** 任务继续处理下一个 chunk，不中断整体流程

#### Scenario: 任务单实例互斥

- **WHEN** 多实例部署时定时任务并发触发
- **THEN** 通过 Redisson 锁 `lock:scheduler:chunk-reprocess` 互斥
- **AND** 仅一个实例执行，其他实例跳过
