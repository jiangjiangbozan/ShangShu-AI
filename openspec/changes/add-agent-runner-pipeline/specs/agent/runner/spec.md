## Purpose

Agent 执行器策略化：统一 `AgentRunner` 抽象，实现 CoT（ReAct 文本解析）与 FC（模型原生 Function Calling）两种执行策略，经 `StrategyFactory` 按 `agent_runtime.strategy` 动态路由，向上层屏蔽底层执行差异。

## ADDED Requirements

### Requirement: AgentRunner 统一抽象

系统 SHALL 提供 `AgentRunner` 接口，约束统一入口方法：`strategy()` / `run(runtimeId)` / `step(runtimeId)` / `pause(runtimeId)` / `resume(runtimeId)`。

- `run` SHALL 完整执行 Agent 任务直至终态（DONE / PAUSED / FAILED）
- `step` SHALL 单步执行（CoT：一个 Thought→Action→Observation 循环；FC：一次完整工具循环回合）
- 所有 Runner 输出 MUST 统一为 `RunnerOutput`（`StepResult` = CONTINUE / DONE / PAUSED + finalAnswer / error / stepIndex / pauseState）
- `AgentRuntimeService` MUST 仅面向 `AgentRunner` 抽象编程，禁止感知具体执行器

#### Scenario: 两种策略输出统一

- **GIVEN** `CotAgentRunner` 与 `FcAgentRunner` 均实现 `AgentRunner`
- **WHEN** 两个 Runner 分别完成同一任务的 run
- **THEN** 两者均返回 `RunnerOutput`（result / finalAnswer / stepIndex / runtimeId 字段齐全）
- **AND** `AgentRuntimeService` 无需感知底层策略差异

### Requirement: CotAgentRunner（ReAct 文本解析策略）

系统 SHALL 提供 `CotAgentRunner` 实现 ReAct 式 Thought→Action→Observation 循环（原 ReactEngine 改造）：

1. 管线 REQUEST：AskHuman 暂停检测 → 上下文注入 → 技能目录 → 提示词组装
2. Thought：LLM 推理，OutputLayer 解析 JSON（thought / action / final_answer / ask_human）
3. Action：校验入参后经 SkillRegistry 调用（回调管线自动落 trace）
4. Observation：结果存入 AgentContext + 快照落盘
5. 终态：`final_answer` 结束转 done；`ask_human` 生成 ASK_HUMAN 暂停；超过 `agent.react.max-iterations` 转 failed

#### Scenario: 3 步任务完成

- **GIVEN** user query 需要一次工具调用
- **WHEN** `CotAgentRunner.run(runtimeId)`
- **THEN** step 0 输出 thought + action → 调用 Skill 写入 observation → step 1 输出 final_answer
- **AND** runtime 转 done，`agent_trace` 存在 thought/action/observation 记录
- **AND** 每步调用 `AgentContextManager.snapshot` 持久化

#### Scenario: 模型输出 ask_human

- **WHEN** LLM 输出 `{"thought":"...","ask_human":{"question":"...","variables":{...}}}`
- **THEN** `CotAgentRunner` 生成 ASK_HUMAN 类型 `PauseState` 并挂起
- **AND** 返回 `RunnerOutput.paused`，状态机转 paused

#### Scenario: 超过最大迭代数

- **WHEN** LLM 持续输出 action 不收敛达到 `agent.react.max-iterations`
- **THEN** runtime 转 failed，`error_message=max iterations reached`
- **AND** 最后一步 `agent_trace.error` 标记

### Requirement: FcAgentRunner（原生 Function Calling 策略）

系统 SHALL 提供 `FcAgentRunner` 基于模型原生 Function Calling 执行，不再正则解析思维文本：

- 管线 REQUEST：暂停检测 → 上下文注入 → ToolLayer 将 SkillRegistry 注册的 Skill 包装为 `SkillToolCallback` → PromptLayer 组装 FC 角色指令
- 通过 `ChatClient.prompt().system().user().tools(toolCallbacks).call()` 执行，工具循环（模型发调用→执行→回填→再推理）由 ChatClient 内部托管
- 模型最终输出即最终答案；每个 `SkillToolCallback.call` 分配独立 stepIndex，经 SkillRegistry 触发回调自动落 trace
- 暂停为整体级 PauseState（不做步内挂起）

#### Scenario: 原生工具调用完成

- **GIVEN** runtime.strategy = FC 且注册 ≥ 1 个 Skill
- **WHEN** `FcAgentRunner.run(runtimeId)`
- **THEN** ChatClient 携带 tools() 调用模型，工具调用经 SkillToolCallback 执行
- **AND** 模型输出非空时 runtime 转 done，返回最终答案
- **AND** 每次工具调用在 `agent_trace` 落 action 记录

#### Scenario: LLM 调用失败

- **WHEN** ChatClient 调用抛异常或返回空
- **THEN** runtime 转 failed，`error_message` 记录原因
- **AND** 返回 `RunnerOutput.failed`

### Requirement: StrategyFactory 动态路由

系统 SHALL 提供 `StrategyFactory` 按执行策略选择 Runner：

- 启动时 SHALL 收集所有 `AgentRunner` Bean，按 `strategy()` 自声明构建 `EnumMap<AgentStrategy, AgentRunner>`
- `getRunner(String)` MUST 大小写不敏感解析；未知策略 MUST 抛 `AGENT_STRATEGY_NOT_SUPPORTED`
- `agent_runtime.strategy` 为空（旧数据）时 MUST 默认使用 COT

#### Scenario: 按策略路由

- **GIVEN** strategy = "FC"
- **WHEN** `AgentTaskConsumer` 消费任务调用 `strategyFactory.getRunner("fc")`
- **THEN** 返回 `FcAgentRunner` 实例
- **AND** COT 对应 `CotAgentRunner`

#### Scenario: 未知策略

- **WHEN** 调用 `getRunner("UNKNOWN")`
- **THEN** 抛 `BusinessException(AGENT_STRATEGY_NOT_SUPPORTED)`
