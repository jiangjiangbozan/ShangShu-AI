## Purpose

统一工具回调管线：定义 `skill_before_invoke` / `skill_after_invoke` / `skill_error` 回调事件，`SkillInvoker` 执行技能前后触发回调，统一记录入参、出参、耗时、异常；回调内部对接 `TraceRecorder` 自动写入 `agent_trace`，支持外部注册自定义处理器扩展告警、监控上报。

## ADDED Requirements

### Requirement: 回调抽象

系统 SHALL 提供：

- `CallbackEvent` 枚举：`SKILL_BEFORE_INVOKE` / `SKILL_AFTER_INVOKE` / `SKILL_ERROR`
- `SkillCallbackContext`：承载 event / runtimeId / stepIndex / skillName / skillVersion / input / output / latencyMs / error
- `CallbackHandler` 接口：`onEvent(SkillCallbackContext)`，处理器实现 MUST 容忍任何异常（不向调用方传播）
- `CallbackDispatcher`：SHALL 遍历所有注册的 `CallbackHandler` 广播事件，单处理器异常 MUST 被捕获仅告警，不影响 Skill 主流程

#### Scenario: 广播到所有处理器

- **WHEN** `dispatch(ctx)` 且注册 2 个 handler
- **THEN** 两个 handler 均收到事件
- **AND** 其中一个 handler 抛异常时，另一个仍被调用

### Requirement: SkillInvoker 统一埋点

`SkillInvoker.invoke(def, input, runtimeId, stepIndex)` SHALL 在以下时点触发回调：

- 调用前：`SKILL_BEFORE_INVOKE`（入参埋点）
- 调用成功：`SKILL_AFTER_INVOKE`（出参 + latencyMs）
- 调用异常（BusinessException）：`SKILL_ERROR`（异常信息 + latencyMs）

回调异常 MUST 不影响 Skill 主流程（dispatch 内部捕获）。`runtimeId` / `stepIndex` 为 null（非 Agent 编排场景）时回调照发，由处理器决定是否落库。

#### Scenario: 成功调用回调序列

- **WHEN** Skill 调用成功（runtimeId / stepIndex 非空）
- **THEN** 依次触发 BEFORE → AFTER
- **AND** AFTER 的 latencyMs 为实际耗时，output 为 Skill 返回

#### Scenario: 失败调用回调序列

- **WHEN** Skill 调用抛 BusinessException
- **THEN** 依次触发 BEFORE → ERROR
- **AND** ERROR 的 error 字段含异常消息

### Requirement: TraceCallbackHandler 自动落库

系统 SHALL 提供内置 `TraceCallbackHandler`：

- `SKILL_AFTER_INVOKE` → `TraceRecorder.recordAction`（skill 入参 / 出参 / 耗时，step_type=action）
- `SKILL_ERROR` → `TraceRecorder.recordError`（step_type=error）
- `SKILL_BEFORE_INVOKE` → 不落库（避免与 AFTER 重复，供监控 / 审计类自定义处理器消费）
- `stepIndex` 为 null MUST 跳过落库（避免破坏 `(runtime_id, step_index, step_type)` 唯一约束）

#### Scenario: 自动落 trace

- **WHEN** `SkillRegistry.invoke(runtimeId, stepIndex, skillName, "latest", input)` 成功
- **THEN** `agent_trace` 自动新增 action 记录（skill_id = skillName，input/output/latency_ms 齐全）
- **AND** 业务代码无任何手动 TraceRecorder 调用

### Requirement: 自定义处理器扩展

系统 SHALL 支持外部注册自定义 `CallbackHandler`（实现接口并注册为 Spring Bean 即自动纳入派发），可扩展告警、监控上报、审计。

#### Scenario: 注册监控处理器

- **WHEN** 新增 `MetricsCallbackHandler implements CallbackHandler` 并注册为 Bean
- **THEN** 无需改动 SkillInvoker / Dispatcher，新处理器自动接收全部 Skill 回调事件
