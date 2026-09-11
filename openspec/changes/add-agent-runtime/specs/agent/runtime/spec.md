## Purpose

Agent Runtime 引擎实现 Agent 全生命周期管理：状态机、异步调度、断点续跑、上下文管理。支持长程任务跨实例恢复，覆盖 M4 验收指标"断点续跑 95% 恢复"。

## ADDED Requirements

### Requirement: Runtime 生命周期状态机

系统 SHALL 提供 `AgentRuntime` 实体，状态字段取值固定为 `pending | running | paused | done | failed | cancelled`，状态转移 MUST 满足：

- `pending → running`：调用 `start` 接口触发
- `running → paused`：调用 `pause` 接口或被动等待外部输入触发
- `paused → running`：调用 `resume` 接口，从 `context_snapshot` 还原执行点
- `running → done`：任务正常完成
- `running → failed`：执行异常或 `last_heartbeat` 超 TTL 被扫描转 `failed`
- `running | paused → cancelled`：用户主动取消

非法转移（如 `done → running`）MUST 抛 `BusinessException(ErrorCode.AGENT_INVALID_STATE_TRANSITION)`。

#### Scenario: 创建并启动 runtime

- **WHEN** `POST /api/v1/agent/runtimes` 携带 `sessionId` + `agentConfigId`
- **THEN** 返回 `{"code":0,"data":{"runtimeId":<id>,"status":"pending"}}`
- **AND** `agent_runtime` 表写入一条记录，`status=pending`，`last_heartbeat=now()`
- **WHEN** `POST /api/v1/agent/runtimes/{id}/start`
- **THEN** 状态转 `running`，异步任务派发到 `agent.task.dispatch` 队列

#### Scenario: 非法状态转移拒绝

- **WHEN** runtime 当前状态为 `done`
- **AND** 调用 `POST /api/v1/agent/runtimes/{id}/pause`
- **THEN** 返回 `{"code":40009,"message":"非法状态转移: done → paused"}`
- **AND** 数据库状态不变

#### Scenario: 重启后超时任务转 failed

- **GIVEN** `agent.heartbeat-ttl=300s` 配置
- **WHEN** 应用启动时扫描 `status=running AND last_heartbeat < now() - ttl` 的 runtime
- **THEN** 状态转 `failed`，`error_message=heartbeat timeout`
- **AND** 触发 `agent.state.notify` 事件，前端 SSE 推送状态变更

### Requirement: 断点续跑与上下文快照

系统 SHALL 在 ReactEngine 每步执行成功后调用 `AgentContextManager.snapshot()`，将完整上下文（chat history + observation 池 + thought chain）序列化为 JSON 落库到 `agent_runtime.context_snapshot` 字段，并更新 `last_heartbeat`。

#### Scenario: 每步落快照

- **WHEN** ReactEngine 完成 step_index=3 的 Action 调用
- **THEN** 调用 `AgentContextManager.snapshot()` 序列化上下文
- **AND** `UPDATE agent_runtime SET context_snapshot = ?, last_heartbeat = now() WHERE id = ?`
- **AND** 同时写 Redis `agent:ctx:{runtimeId}`（TTL 24h）作为热缓存

#### Scenario: 从快照恢复

- **GIVEN** runtime 状态为 `paused`，`context_snapshot` 非空
- **WHEN** 调用 `POST /api/v1/agent/runtimes/{id}/resume`
- **THEN** 从 `context_snapshot` 反序列化恢复 `AgentContext`
- **AND** 状态转 `running`，从 `next_step_index` 继续执行
- **AND** 写入 `agent_trace` 一条 `step_type=resume` 的记录

#### Scenario: 续跑失败兜底

- **WHEN** `context_snapshot` JSON 解析失败或字段缺失
- **THEN** 状态保持 `failed`，`error_message=context_snapshot corrupted`
- **AND** 不重新派发任务，避免循环失败

### Requirement: 异步任务调度

长程任务 MUST 通过 RabbitMQ `agent.task.dispatch` 队列异步执行，禁止在 SSE 主线程内同步阻塞。

#### Scenario: 派发异步任务

- **WHEN** runtime 调用 `start`
- **THEN** 向 `agent.task.dispatch` 队列发送消息 `{"runtimeId":<id>,"sessionId":<sid>}`
- **AND** 立即返回 `{"code":0,"data":{"status":"running"}}`，不等待任务完成

#### Scenario: 任务消费失败入死信

- **WHEN** 消费者执行 ReactEngine 抛异常
- **THEN** 消息进入 `agent.task.dlq` 死信队列
- **AND** runtime 状态转 `failed`，`error_message` 关联死信消息 ID `amq.gen-xxx`

### Requirement: 上下文统一管理

`AgentContextManager` SHALL 统一管理三类上下文：

1. **chat history**：用户与 Agent 的对话消息列表
2. **observation 池**：所有 Skill 调用结果，按 `skillCallId` 索引
3. **thought chain**：LLM 推理中间态，按 step_index 顺序

#### Scenario: 三类上下文序列化

- **WHEN** 调用 `snapshot()`
- **THEN** 返回 JSON `{"chatHistory":[...],"observations":{"<callId>":{...}},"thoughtChain":[...],"nextStepIndex":N}`
- **AND** JSON 大小 ≤ 64KB（超限触发 `MemoryStore.compress` 压缩）

#### Scenario: 跨实例迁移

- **GIVEN** runtime 在实例 A 上运行，因 A 宕机需在实例 B 恢复
- **WHEN** 实例 B 调用 `resume`
- **THEN** 从 MySQL `context_snapshot` 加载（不依赖实例本地内存）
- **AND** ReactEngine 从 `nextStepIndex` 续跑，行为与 A 一致
