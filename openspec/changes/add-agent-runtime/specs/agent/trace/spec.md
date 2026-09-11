## Purpose

全链路 Trace 体系实现结构化记录、回放 API、性能埋点与错误归因，支撑回放调试与成本归因，覆盖 M4 验收指标"Trace 100% 可回放"。

## ADDED Requirements

### Requirement: 结构化 Trace 写入

系统 SHALL 在 ReactEngine 每步执行后向 `agent_trace` 表写入一条记录，字段包括：

- `trace_id`：UUID 主键
- `runtime_id`：关联 runtime
- `step_index`：步骤序号（从 0 递增）
- `step_type`：`thought | action | observation | resume | error`
- `skill_id`：action 类型必填，其他类型为空
- `input`：JSON，action 的输入 / thought 的 prompt
- `output`：JSON，action 的返回 / thought 的 LLM 响应
- `token_in` / `token_out`：LLM 调用 token 数（非 LLM 步骤为 0）
- `latency_ms`：本步耗时
- `parent_step_id`：子任务嵌套时关联父步骤
- `error`：异常信息（null 表示成功）

写入 MUST 异步执行，通过 `agent.trace.collect` RabbitMQ 队列解耦主流程，禁止阻塞 ReactEngine。

#### Scenario: 正常步骤写入

- **WHEN** ReactEngine 完成 step_index=2 的 action 调用 web_search
- **THEN** 发送 MQ 消息 `{"runtimeId":1,"stepIndex":2,"stepType":"action","skillId":"web_search","input":{...},"output":{...},"latencyMs":150}`
- **AND** 消费者写入 `agent_trace` 表
- **AND** `uk_runtime_step (runtime_id, step_index)` 唯一索引防重复

#### Scenario: 异常步骤标记

- **WHEN** step 3 调用 `code_executor` 抛异常
- **THEN** `agent_trace.error = "code_executor timeout"`
- **AND** 关联死信消息 ID `amq.gen-xxx`（来自 `agent.task.dlq`）
- **AND** runtime 状态转 `failed`

#### Scenario: 子任务嵌套

- **WHEN** step 5 调用 subagent Skill，子 runtime 内部执行 3 步
- **THEN** 子 runtime 的 trace `parent_step_id = <parent_step_id of step 5>`
- **AND** 回放接口按 `parent_step_id` 递归返回子链路

### Requirement: 回放接口

`GET /api/v1/agent/runtimes/{runtimeId}/replay` SHALL 按 `step_index` 升序返回完整链路。

#### Scenario: 完整链路回放

- **WHEN** 调用 `GET /api/v1/agent/runtimes/123/replay`
- **THEN** 返回 `{"code":0,"data":{"runtimeId":123,"status":"done","steps":[{...},{...},...]}}`
- **AND** steps 按 step_index 升序，包含所有 thought/action/observation

#### Scenario: runtime 不存在

- **WHEN** 调用 `GET /api/v1/agent/runtimes/99999/replay`
- **THEN** 返回 `{"code":40401,"message":"runtime not found: 99999"}`

#### Scenario: 子任务链路递归返回

- **GIVEN** runtime 123 step 5 是 subagent，子 runtime 456
- **WHEN** 调用 `GET /api/v1/agent/runtimes/123/replay`
- **THEN** step 5 包含 `children` 字段，列出 runtime 456 的 steps

### Requirement: 性能埋点与成本归因

每步 `latency_ms / token_in / token_out` MUST 记录，`GET /api/v1/agent/runtimes/{id}/metrics` 汇总返回：

#### Scenario: 成本汇总

- **WHEN** 调用 `GET /api/v1/agent/runtimes/123/metrics`
- **THEN** 返回 `{"totalLatencyMs":4500,"totalTokenIn":1200,"totalTokenOut":800,"stepCount":5,"skillCalls":{"web_search":1,"llm":2}}`
- **AND** 按 step_type 与 skillId 分别聚合统计
