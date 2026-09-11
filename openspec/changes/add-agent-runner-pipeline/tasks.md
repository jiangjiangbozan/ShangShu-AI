# 实施任务 - add-agent-runner-pipeline

> 任务粒度 ≤ 4 小时；按数据层 → 实体/Mapper → 能力层 → 业务层 → 接入层顺序执行；每个任务标注验证方式。
>
> 状态：已完成（2026-09-08）

## 阶段 1: 数据层与配置

### Task 1.1: 数据库迁移脚本

- [x] 新增 `api/src/main/resources/db/migration/V20260922__add_agent_pause_strategy.sql`
- [x] `agent_runtime` 新增 `strategy VARCHAR(20) NOT NULL DEFAULT 'COT'`
- [x] 新建 `agent_pause_state` 表（next_step_index / pause_type / question / variables / reply / status + idx_runtime_status）
- [x] `agent_trace` 唯一键升级为 `(runtime_id, step_index, step_type)`
- **验证**：应用启动 Flyway 自动迁移；`SHOW CREATE TABLE agent_runtime` 含 strategy 字段

### Task 1.2: 执行策略枚举与常量

- [x] 新增 `AgentStrategy`（COT / FC）
- [x] 新增 `StepResult`（CONTINUE / DONE / PAUSED）
- [x] `ErrorCode` 新增 `AGENT_STRATEGY_NOT_SUPPORTED` / `AGENT_PAUSE_NOT_FOUND` / `AGENT_PAUSE_ALREADY_EXISTS`
- **验证**：`mvn compile` 通过

## 阶段 2: 执行器策略化（模块 1）

### Task 2.1: AgentRunner 统一抽象

- [x] 新增 `AgentRunner` 接口：`strategy() / run(runtimeId) / step(runtimeId) / pause(runtimeId) / resume(runtimeId)`
- [x] 新增 `RunnerOutput` 统一输出模型（result / finalAnswer / error / stepIndex / runtimeId / pauseState + 4 个静态工厂）
- **验证**：`mvn compile` 通过

### Task 2.2: CotAgentRunner（原 ReactEngine 改造）

- [x] 新增 `CotAgentRunner implements AgentRunner`，实现 ReAct 循环（Thought→Action→Observation）
- [x] 管线驱动：每步执行 `AgentPipeline.run(Phase.REQUEST)` → LLM → `AgentPipeline.run(Phase.RESPONSE)` → Action → Observation
- [x] final_answer / ask_human / 无 action 收尾 / 超时护栏（partial finalize）四条终止路径
- [x] Skill 调用失败：publishError + fail，不继续循环
- [x] 超过 max-iterations：recordError + fail
- **验证**：mock ChatClient 输出固定 JSON，3 步任务完成；ask_human 触发暂停

### Task 2.3: FcAgentRunner（原生 Function Calling）

- [x] 新增 `FcAgentRunner implements AgentRunner`，基于 ChatClient 原生工具循环
- [x] 管线 REQUEST：暂停检测 → 上下文注入 → ToolLayer 构建 ToolCallback → PromptLayer 组装 FC 角色指令
- [x] 模型输出即最终答案，无正则解析
- [x] 新增 `SkillToolCallback implements ToolCallback`：Skill 包装为原生工具（name/description/inputSchema + call 执行）
- **验证**：mock ChatClient 验证 tools() 注册与工具执行路径

### Task 2.4: StrategyFactory 动态路由

- [x] 新增 `StrategyFactory`：启动收集所有 AgentRunner，构建 `EnumMap<AgentStrategy, AgentRunner>`
- [x] `getRunner(AgentStrategy)` / `getRunner(String)`（大小写不敏感）
- [x] 未知策略抛 `AGENT_STRATEGY_NOT_SUPPORTED`
- **验证**：单测覆盖 COT/FC/未知策略三条路径

## 阶段 3: 分层管线（模块 2）

### Task 3.1: Layer 抽象 + AgentPipeline 编排器

- [x] 新增 `Layer` 接口（`phase() / order() / process(PipelineContext)`）+ `Phase`（REQUEST / RESPONSE）
- [x] 新增 `AgentPipeline`：按 Phase 分组、按 order 排序，`run(phase, ctx)` 顺序串联
- [x] 新增 `PipelineContext`：统一承载 userQuery / strategy / agentContext / systemPrompt / skillCatalog / toolCallbacks / contextBlock / pauseState / modelOutput / parsedOutput / paused / finalAnswer / error
- **验证**：单测验证 REQUEST 层按 order 10→20→30 顺序执行

### Task 3.2: 五个 Layer 实现

- [x] `AskHumanLayer`（order=5）：检测活跃 PauseState → `ctx.paused=true` + 注入 pauseState
- [x] `ContextLayer`（order=10）：快照上下文注入 + userQuery 回填 + 会话窗口裁剪 + contextBlock 组装
- [x] `ToolLayer`（order=20）：FC 构建 ToolCallback 数组 / COT 生成技能目录 / `validateSkillInput` 入参校验
- [x] `PromptLayer`（order=30）：COT ReAct 模板 + FC Function Calling 角色指令 + 超时收尾模板
- [x] `OutputLayer`（Phase.RESPONSE，order=10）：COT 解析 JSON（容错提取首个平衡对象）/ FC 透传最终答案
- **验证**：单测覆盖各 Layer 正常 / 异常路径

## 阶段 4: 轻量暂停恢复（模块 3）

### Task 4.1: PauseState 实体 + Mapper + Service

- [x] 新增 `PauseState`（@TableName("agent_pause_state")，variables 用 JacksonTypeHandler）
- [x] 新增 `PauseStateMapper`（BaseMapper + `selectActive(runtimeId)` 查 WAITING 最新）
- [x] 新增 `PauseStateService`：create（自动取消旧活跃）/ findActive / isPaused / resolve / cancel
- **验证**：单测覆盖 create→find→resolve→cancel 生命周期

### Task 4.2: AgentRuntimeService 适配

- [x] `createRuntime` 增加 strategy 参数（默认 COT，大写归一）
- [x] `pause`：状态机 running→paused + 落 PauseState(NORMAL)
- [x] `resume`：状态机 paused→running + cancel 暂停点 + 重派任务
- [x] 新增 `askHuman`：生成 ASK_HUMAN 暂停点
- [x] 新增 `respond`：resolve 暂停 + 人工回复回填 `observations["ask_human_response"]` + 从 nextStepIndex 续跑
- [x] `heartbeat` 跳过 PAUSED 状态（避免暂停期间刷新心跳掩盖真实状态）
- **验证**：单测覆盖状态机全路径 + respond 上下文回填

### Task 4.3: Controller 扩展

- [x] `AgentRuntimeController`：createRuntime 支持 strategy；新增 ask-human / respond 端点
- **验证**：curl 调用 pause → ask-human → respond 全链路

## 阶段 5: 统一回调管线（模块 4）

### Task 5.1: 回调抽象

- [x] 新增 `CallbackHandler` 接口（`onEvent(SkillCallbackContext)`）
- [x] 新增 `CallbackEvent`（SKILL_BEFORE_INVOKE / SKILL_AFTER_INVOKE / SKILL_ERROR）
- [x] 新增 `SkillCallbackContext`（event / runtimeId / stepIndex / skillName / skillVersion / input / output / latencyMs / error）
- [x] 新增 `CallbackDispatcher`：遍历所有 handler 广播，单处理器异常隔离
- **验证**：`mvn compile` 通过

### Task 5.2: SkillInvoker 埋点 + TraceCallbackHandler

- [x] `SkillInvoker.invoke(def, input, runtimeId, stepIndex)`：调用前/成功/异常三处 dispatch 回调
- [x] 新增 `TraceCallbackHandler`：SKILL_AFTER_INVOKE → recordAction；SKILL_ERROR → recordError；BEFORE 不落库
- [x] `SkillRegistry.invoke(runtimeId, stepIndex, skillName, version, input)` 重载透传
- [x] DagEngine / PlanAndExecuteEngine / CotAgentRunner / SkillToolCallback 切换新重载
- **验证**：单测验证回调触发次数与 trace 落库内容

## 阶段 6: 思维链重建（模块 5）

### Task 6.1: TraceService 扩展

- [x] `rebuildChain(runtimeId)`：按 step_index 聚合 thought/action/observation/error/resume，返回结构化数组
- [x] `rebuildChain(runtimeId, page, size)`：分页获取
- [x] `parseJson` 容错：非法 JSON 原样返回文本
- **验证**：单测覆盖多类型同 step 聚合与分页边界

### Task 6.2: TraceController 扩展

- [x] `GET /api/v1/agent/trace/{runtimeId}/chain`（page/size 可选分页）
- **验证**：curl 返回完整思维链数组

## 阶段 7: 清理与适配（模块 6）

### Task 7.1: 删除 ReactEngine 与遗留引用

- [x] 删除 `agent/react/ReactEngine.java`
- [x] 更新 TraceRecorder / SessionService / AgentEventBus / CustomerServiceController / AgentRuntimeMapper 注释中的 ReactEngine 引用
- **验证**：`grep -r ReactEngine` 无代码引用（仅历史文档）

### Task 7.2: 编译与格式验证

- [x] `mvn compile` 通过
- [x] `mvn spotless:apply` 修复全部格式违规
- [x] `mvn compile` 复验通过
- **验证**：两条命令 exit code = 0

## 阶段 8: 文档更新

### Task 8.1: OpenSpec 工件

- [x] 新增 proposal.md / design.md / tasks.md / specs（runner / pipeline / pause / callback / trace）
- **验证**：工件齐全、SPEC 用 SHALL/MUST + Scenario

### Task 8.2: 知识库文档

- [x] 更新 `docs/kb/agent/react-engine.md`：ReactEngine → AgentRunner 策略化 + 分层管线说明
- **验证**：文档与代码实现一致
