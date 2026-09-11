## Why

`add-agent-runtime` 交付的 `ReactEngine` 存在四个工程化短板，阻碍 Agent 能力对齐 Dify：

1. **执行策略硬编码**：`ReactEngine` 内嵌唯一的 CoT（文本正则解析）循环，`Thought→Action→Observation` 依赖模型严格输出 JSON，鲁棒性差；无法切换到模型原生 Function Calling 策略，不同场景（低成本文本模型 vs 工具调用能力强的模型）无法选型。
2. **引擎与业务逻辑耦合**：上下文加载、提示词组装、工具注册、输出解析全部堆在引擎内部，无法独立替换/扩展（如新增"输出格式校验"或"人工介入"都不改动引擎）。
3. **暂停恢复粒度粗**：暂停/续跑依赖 `AgentContextManager` 全量快照序列化（chat history + observations 全量 JSON），数据量大、恢复慢；人工介入（ask-human）场景没有独立的轻量暂停点与标准流程。
4. **可观测性埋点散落**：Skill 调用成功/失败/耗时的记录散落在业务代码中，没有统一回调入口，外部监控/告警/审计无法以插件方式接入。

对标 Dify 的 `AgentStrategy`（Function Calling / ReAct）+ `AgentPipeline`（分层组合）+ `MessageAgentThought`（思维链重建）+ 人工介入（pause 状态），本 change 将 ReactEngine 改造为**执行器策略化 + 分层管线 + 轻量暂停 + 统一回调**，并为 `agent_trace` 补上思维链重建查询能力。

## What Changes

### 1. 执行器策略化（Runner 抽象）

- 新增统一抽象 `AgentRunner` 接口：`run / step / pause / resume` + `strategy()` 自声明策略
- 原 `ReactEngine` 改造为 `CotAgentRunner`（ReAct 文本解析策略），新增 `FcAgentRunner`（Spring AI 原生 Function Calling 策略）
- 新增 `StrategyFactory`：按 `agent_runtime.strategy`（COT | FC）动态选择 Runner
- 两个 Runner 输出统一为 `RunnerOutput`（CONTINUE / DONE / PAUSED + finalAnswer / error / pauseState），向上层 `AgentRuntimeService` 屏蔽差异
- `AgentTaskConsumer` 消费任务时经 `StrategyFactory` 路由执行器，替代直接调用 ReactEngine

### 2. 分层管线（Agent Pipeline）

- 新增可插拔 `Layer` 接口 + `AgentPipeline` 编排器（按 Phase 分组、按 order 排序串联）
- 新增 5 个 Layer：
  - `PromptLayer`：系统提示词 / 角色设定 / Agent 指令组装（COT 与 FC 两套模板）
  - `ContextLayer`：会话历史注入 + 上下文窗口裁剪 + 快照上下文恢复（对接 AgentContextManager）
  - `ToolLayer`：工具注册（FC 构建 ToolCallback 数组）/ 技能目录文本（COT）/ 入参校验（对接 SkillRegistry）
  - `OutputLayer`：模型输出解析 / 结构化输出 / 格式校验（容错提取首个平衡 JSON）
  - `AskHumanLayer`：检测活跃 PauseState，置位 `ctx.paused` 挂起执行
- Layer 之间仅传递统一上下文对象 `PipelineContext`；`AgentRuntimeService` 只负责调度管线 + 状态机

### 3. 轻量暂停恢复（PauseState + AskHumanLayer）

- 新增 `PauseState` 实体 + `agent_pause_state` 表：`nextStepIndex` / `pauseType`（NORMAL | ASK_HUMAN）/ `variables` / `reply` / `status`
- 新增 `PauseStateService`：create / findActive / resolve / cancel，保证单活跃暂停点
- 新增 `AskHumanLayer`：每步执行前检测暂停，命中即挂起
- `AgentRuntimeService.pause / resume / askHuman / respond` 适配 PauseState：人工回复经 `respond` 回填上下文（`observations["ask_human_response"]`）后从 `nextStepIndex` 续跑
- 兼容快照：全量快照降级为异常崩溃完整恢复兜底，暂停场景优先使用轻量 PauseState

### 4. 统一工具回调管线

- 新增 `CallbackHandler` 接口 + `CallbackDispatcher` 广播器 + `CallbackEvent`（`skill_before_invoke` / `skill_after_invoke` / `skill_error`）+ `SkillCallbackContext`
- `SkillInvoker` 调用前后触发回调，统一记录入参 / 出参 / 耗时 / 异常
- 内置 `TraceCallbackHandler`：回调自动对接 `TraceRecorder` 写入 `agent_trace`，业务代码零侵入
- 支持外部注册自定义处理器（告警 / 监控上报 / 审计）

### 5. 思维链重建查询

- `TraceService` 新增 `rebuildChain(runtimeId)`：按 step_index 聚合 thought / action / observation / error / resume，返回完整结构化思维链数组
- 新增 `rebuildChain(runtimeId, page, size)` 分页能力
- 新增 `GET /api/v1/agent/trace/{runtimeId}/chain` 接口（分页可选），对标 Dify MessageAgentThought + MessageChain
- 修复 `agent_trace` 唯一键：`(runtime_id, step_index)` → `(runtime_id, step_index, step_type)`，支持同一步多类型记录

## Capabilities

### New Capabilities

- `agent/runner`: 执行器策略化抽象，AgentRunner 接口 + Cot/Fc 两个实现 + StrategyFactory 动态路由
- `agent/pipeline`: 分层管线，Prompt/Context/Tool/Output/AskHuman 五层可插拔组合
- `agent/pause`: 轻量暂停恢复，PauseState + agent_pause_state 表 + ask-human 人工介入
- `agent/callback`: 统一工具回调，skill_before/after/error 事件 + TraceRecorder 自动落库

### Modified Capabilities

- `agent/runtime`: AgentRuntimeService 适配 Runner 抽象与 PauseState；`agent_runtime` 新增 `strategy` 字段
- `agent/trace`: TraceService 新增思维链重建与分页；`agent_trace` 唯一键升级为三步复合
- `agent/skill`: SkillInvoker 集成统一回调；SkillRegistry 新增带 runtimeId/stepIndex 的 invoke 重载
- `agent/react-engine`: 由 `CotAgentRunner` 取代（改造复用，行为向前兼容）

## Impact

- **数据库**：迁移脚本 `V20260922__add_agent_pause_strategy.sql`
  - `agent_runtime` 新增 `strategy VARCHAR(20) NOT NULL DEFAULT 'COT'`
  - 新建 `agent_pause_state` 表
  - `agent_trace` 唯一键改为 `(runtime_id, step_index, step_type)`
- **后端代码**：
  - 新增 `agent/runner/`（AgentRunner / AgentStrategy / StepResult / RunnerOutput / StrategyFactory / CotAgentRunner / FcAgentRunner / SkillToolCallback）
  - 新增 `agent/pipeline/`（Layer / AgentPipeline / PipelineContext / PromptLayer / ContextLayer / ToolLayer / OutputLayer / AskHumanLayer）
  - 新增 `agent/pause/`（PauseState / PauseStateMapper / PauseStateService）
  - 新增 `agent/callback/`（CallbackHandler / CallbackDispatcher / CallbackEvent / SkillCallbackContext / TraceCallbackHandler）
  - 删除 `agent/react/ReactEngine.java`
  - 改造 `SkillInvoker` / `SkillRegistry` / `AgentRuntimeService` / `AgentTaskConsumer` / `TraceService` / `TraceController`
- **配置**：无新增依赖，复用 Spring AI ChatClient + ToolCallback 机制
- **关联 change**：依赖 `add-agent-runtime`；阻塞后续多 Agent 协作（subagent）与策略自动选择

## 非目标

- **不做** 策略自动推荐/选择（基于任务类型或模型能力自动选 COT/FC，V2 结合模型元数据）
- **不做** ReAct 正则解析的强鲁棒性兜底（V2 引入结构化输出约束 schema）
- **不做** FC 策略步内挂起（FC 工具循环由 ChatClient 托管，暂停为整体级 PauseState）
- **不做** 回调的异步/批量聚合（回调处理器同步执行，批量上报留给监控类自定义处理器）
- **不做** 暂停超时自动取消（ask-human 长时间无回复的 TTL 策略留 V2）

## 验收标准

1. `POST /api/v1/agent/runtime` 创建 runtime 可指定 `strategy=COT|FC`，`agent_runtime.strategy` 落库，默认 COT
2. `AgentTaskConsumer` 消费任务时按 strategy 路由到对应 Runner（COT → CotAgentRunner，FC → FcAgentRunner），两者均产出统一 RunnerOutput
3. `CotAgentRunner` 完成 3 步 ReAct 任务（thought→action→observation→final_answer），每步写 `agent_trace`
4. `FcAgentRunner` 通过模型原生 Function Calling 完成工具循环，无正则解析思维文本，工具调用经回调管线自动落 trace
5. `POST /api/v1/agent/runtime/{id}/pause` 后落 `agent_pause_state`（NORMAL）且状态机 running→paused；`resume` 从 `next_step_index` 续跑
6. 模型输出 `ask_human` 时生成 ASK_HUMAN 暂停点；`POST /api/v1/agent/runtime/{id}/respond` 回填上下文后从 nextStepIndex 继续
7. `agent_trace` 同一步支持 thought + action + observation 三条记录（新唯一键不冲突）
8. `GET /api/v1/agent/trace/{runtimeId}/chain` 返回按 step 聚合的完整思维链数组，支持 page/size 分页
9. Skill 调用前/后/异常触发 `skill_before_invoke / skill_after_invoke / skill_error` 回调，`agent_trace` 自动写入 action/error 记录
10. `mvn compile` 通过，spotless 格式检查通过
