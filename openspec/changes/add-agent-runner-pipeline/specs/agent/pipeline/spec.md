## Purpose

Agent 执行管线分层解耦：将上下文、提示词、工具、输出处理从引擎内部分离为可插拔 Layer，按 Phase 分组、按 order 排序串联，Layer 之间仅传递统一上下文对象 `PipelineContext`。

## ADDED Requirements

### Requirement: Layer 抽象与编排器

系统 SHALL 提供 `Layer` 接口（`phase() / order() / process(PipelineContext)`）与 `AgentPipeline` 编排器：

- `AgentPipeline` SHALL 按 Phase（REQUEST / RESPONSE）分组收集所有 Layer，组内按 `order()` 升序执行
- 任一 Layer 实现 MUST 可独立替换 / 自定义扩展（实现 `Layer` 接口并注册为 Spring Bean 即自动纳入管线）
- 管线 MAY 被 Runner 在 REQUEST（模型调用前）与 RESPONSE（模型调用后）两个时点驱动

#### Scenario: 管线按序执行

- **GIVEN** REQUEST 阶段注册 AskHumanLayer(order=5) / ContextLayer(order=10) / ToolLayer(order=20) / PromptLayer(order=30)
- **WHEN** `agentPipeline.run(Phase.REQUEST, ctx)`
- **THEN** 四层按 5 → 10 → 20 → 30 顺序执行
- **AND** 后序 Layer 可读取前序 Layer 写入 `PipelineContext` 的产物

### Requirement: PromptLayer

`PromptLayer` SHALL 负责系统提示词、角色设定、Agent 指令组装：

- COT 策略 SHALL 使用 ReAct 模板（角色 + 技能目录 + 用户问题 + 历史观察 + JSON 输出要求）
- FC 策略 SHALL 使用 Function Calling 角色指令（工具由模型原生感知）
- SHALL 提供 `buildTimeoutFinalPrompt` 供超时收尾（不再允许调用工具）

#### Scenario: COT 提示词组装

- **WHEN** ctx.strategy = COT 且 skillCatalog / contextBlock 已由前序 Layer 写入
- **THEN** `ctx.systemPrompt` 为完整 ReAct 模板，含技能目录与历史观察

### Requirement: ContextLayer

`ContextLayer` SHALL 负责会话历史、上下文窗口裁剪、快照上下文注入：

- 未预加载时 SHALL 从 `AgentContextManager.load(runtimeId)` 恢复快照上下文
- SHALL 按 `agent.memory.window-size` 滑动窗口裁剪 chatHistory（保留最近 N 条）
- SHALL 将历史观察与人工回复（`observations["ask_human_response"]`）组装为 `ctx.contextBlock`

#### Scenario: 快照上下文恢复

- **WHEN** ctx.agentContext 为空
- **THEN** ContextLayer 从 AgentContextManager 加载并回填 ctx.agentContext
- **AND** 窗口裁剪后超窗消息被丢弃

### Requirement: ToolLayer

`ToolLayer` SHALL 负责工具注册、工具描述生成、工具入参校验（对接 SkillRegistry）：

- FC 策略 SHALL 为每个 enabled Skill 构建 `SkillToolCallback` 数组写入 `ctx.toolCallbacks`
- COT 策略 SHALL 生成技能目录文本（name + description）写入 `ctx.skillCatalog`
- `validateSkillInput` SHALL 按 `skill_definition.input_schema` 的 required 字段校验，缺失抛 `SKILL_INVOKE_FAILED`

#### Scenario: FC 工具注册

- **WHEN** ctx.strategy = FC 且存在 2 个 enabled Skill
- **THEN** `ctx.toolCallbacks` 长度为 2，每个含 name / description / inputSchema

#### Scenario: 入参校验失败

- **WHEN** 调用 `validateSkillInput(skill, input)` 且 input 缺失 schema 中 required 字段
- **THEN** 抛 `BusinessException(SKILL_INVOKE_FAILED)`，缺失字段名出现在错误消息

### Requirement: OutputLayer

`OutputLayer` SHALL 负责模型输出解析、结构化输出、输出格式校验：

- COT 策略 SHALL 解析模型 JSON（thought / action / final_answer / ask_human），容错提取首个平衡 `{...}` 对象；解析失败 MUST 写 `ctx.error` 终止执行
- FC 策略 SHALL 透传模型输出为 `{"final_answer": output}`（工具循环已由 ChatClient 完成）

#### Scenario: 容错解析

- **WHEN** 模型输出为 ```` ```json {...} ``` ```` 等包裹文本
- **THEN** OutputLayer 提取首个平衡 JSON 对象成功，`ctx.parsedOutput` 含结构化字段

#### Scenario: 非法输出

- **WHEN** 模型输出纯文本无 JSON
- **THEN** `ctx.error` 非空，Runner 终止并标记 failed

### Requirement: AskHumanLayer

`AskHumanLayer` SHALL 专门处理人工介入 / 暂停场景：每步执行前检测活跃 `PauseState`（WAITING），命中时置位 `ctx.paused=true` 并注入 `ctx.pauseState`，Runner 据此停止循环返回 PAUSED。

#### Scenario: 检测到暂停

- **WHEN** runtime 存在活跃 ASK_HUMAN 暂停点且 Runner 进入 REQUEST 管线
- **THEN** `ctx.paused=true`，Runner 返回 `RunnerOutput.paused`
- **AND** 不执行模型调用

#### Scenario: 无暂停放行

- **WHEN** runtime 无活跃暂停点
- **THEN** 管线正常继续，`ctx.paused=false`
