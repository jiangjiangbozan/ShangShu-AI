## Purpose

标准化暂停 / 人工介入机制：新增显式暂停状态对象 `PauseState`（`agent_pause_state` 表），替代暂停场景的全量快照；`AgentRuntimeService` 状态机适配 PauseState，心跳识别暂停状态；快照降级为异常崩溃完整恢复兜底。

## ADDED Requirements

### Requirement: PauseState 实体与持久化

系统 SHALL 提供 `PauseState` 实体（`@TableName("agent_pause_state")`），持久化以下字段：

- `nextStepIndex`：下一步执行序号（resume 后从该步继续）
- `pauseType`：`NORMAL`（普通暂停）| `ASK_HUMAN`（人工介入）
- `question`：ask-human 时向用户展示的问题
- `variables`：待确认变量（JSON，人工介入上下文）
- `reply`：人工回复内容（RESOLVED 时写入）
- `status`：`WAITING` | `RESOLVED` | `CANCELLED`

#### Scenario: 持久化与查询

- **WHEN** `PauseStateService.create(runtimeId, nextStepIndex, pauseType, question, variables)`
- **THEN** `agent_pause_state` 新增 WAITING 记录
- **AND** `findActive(runtimeId)` 返回该记录；同一 runtime 同时仅允许一个活跃暂停点

### Requirement: PauseStateService 生命周期

`PauseStateService` SHALL 管理暂停状态生命周期：

- `create` SHALL 自动取消旧活跃记录（保证单活跃暂停点）
- `resolve(runtimeId, userInput)` SHALL 写入人工回复并置 RESOLVED；无活跃记录 MUST 抛 `AGENT_PAUSE_NOT_FOUND`
- `cancel` SHALL 幂等取消活跃记录（不存在不报错）

#### Scenario: 重复创建

- **GIVEN** runtime 已有 WAITING 暂停点
- **WHEN** 再次 `create`
- **THEN** 旧记录置 CANCELLED，新记录 WAITING

### Requirement: AgentRuntimeService 暂停/恢复适配

`AgentRuntimeService` SHALL 适配 PauseState：

- `pause(runtimeId)` SHALL 状态机 running→paused 并落 NORMAL 暂停点（已存在活跃暂停时不重复创建）
- `resume(runtimeId)` SHALL 状态机 paused→running、取消暂停点、重新派发任务
- `askHuman(runtimeId, question, variables, nextStepIndex)` SHALL 状态机→paused 并创建 ASK_HUMAN 暂停点
- `respond(runtimeId, userInput)` SHALL：解析暂停点 → 人工回复回填 `observations["ask_human_response"]` → 从 `nextStepIndex` 续跑 → 状态机 paused→running 并重新派发
- `heartbeat(runtimeId)` MUST 在 PAUSED 状态跳过心跳刷新（避免暂停期间掩盖真实状态）

#### Scenario: 普通暂停恢复

- **WHEN** `pause(runtimeId)` 且 runtime 状态为 running
- **THEN** 状态转 paused，`agent_pause_state` 落 NORMAL 记录
- **AND** `resume(runtimeId)` 后状态回 running，暂停点置 CANCELLED，任务重新派发

#### Scenario: 人工介入完整链路

- **WHEN** 模型输出 ask_human 触发 `askHuman`
- **THEN** 状态转 paused，ASK_HUMAN 暂停点落库（含 question / variables / nextStepIndex）
- **AND** `respond(runtimeId, "同意")` 后暂停点 RESOLVED，`observations["ask_human_response"]` 含 reply，任务从 nextStepIndex 续跑

#### Scenario: 无活跃暂停点 respond

- **WHEN** 调用 `respond(runtimeId, ...)` 且 runtime 无活跃暂停
- **THEN** 抛 `BusinessException(AGENT_PAUSE_NOT_FOUND)`

### Requirement: 快照兜底兼容

`AgentContextManager` 全量快照 SHALL 保留，作为异常崩溃的完整恢复兜底；暂停场景 MUST 优先使用轻量 PauseState（避免全量序列化开销）。

#### Scenario: 崩溃恢复

- **WHEN** 应用重启后存在 crashed 的 running runtime 且 Redis/MySQL 快照完整
- **THEN** 通过快照恢复 AgentContext，从 `next_step_index` 续跑（PauseState 仅记录暂停点，不承载全量上下文）
