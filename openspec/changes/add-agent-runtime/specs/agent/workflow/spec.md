## Purpose

工作流编排模块实现配置化 DAG 引擎、Plan-and-Execute 任务规划、子任务串联，超越 ReAct 单循环。

## ADDED Requirements

### Requirement: 配置化 DAG 执行

系统 SHALL 提供 `WorkflowDefinition` 实体，`dag` 字段为 JSON，描述节点 + 边 + 条件分支。`DagEngine.execute(workflowId, input)` MUST 按拓扑序执行节点，节点结果按 `inputs` 引用注入。

DAG JSON 结构：

```json
{
  "nodes": [
    {"id":"n1","type":"skill","skillId":"web_search","input":{"query":"$input.q"}},
    {"id":"n2","type":"skill","skillId":"kb_retrieval","input":{"query":"$input.q","kbId":"$input.kbId"}},
    {"id":"n3","type":"llm","prompt":"summarize","inputs":["$n1","$n2"]}
  ],
  "edges":[{"from":"n1","to":"n3"},{"from":"n2","to":"n3"}]
}
```

节点 `type` 取值：`skill | llm | branch`。

#### Scenario: 简单 DAG 执行

- **GIVEN** workflow `wf_search_summary` 含 3 节点（web_search + kb_retrieval + llm 总结）
- **WHEN** `POST /api/v1/agent/workflows/wf_search_summary/execute` input=`{"q":"产品规格","kbId":2}`
- **THEN** 拓扑排序后并行执行 n1 + n2，n3 等待两个依赖完成
- **AND** n3 的 prompt 模板中 `$n1.results` 与 `$n2.chunks` 被实际结果替换
- **AND** 返回 n3 的 LLM 输出作为最终结果

#### Scenario: 节点引用缺失

- **WHEN** 节点 n3 配置 `"inputs":["$n1","$n4"]`，但 dag 中无 n4
- **THEN** 抛 `BusinessException(ErrorCode.WORKFLOW_DAG_INVALID, "unknown node ref: n4")`
- **AND** workflow 不进入执行

#### Scenario: 条件分支

- **GIVEN** n2 为 `branch` 类型，配置 `"condition":"$n1.score > 0.5"`
- **WHEN** n1 输出 score=0.8
- **THEN** 执行 `true` 边指向的节点
- **AND** 不执行 `false` 边指向的节点

#### Scenario: 节点超时

- **WHEN** 单节点执行超过 `agent.workflow.node-timeout=60s`
- **THEN** 中断当前节点调用，runtime 转 `failed`
- **AND** `agent_trace` 记录 `step_type=action, error=node timeout`

### Requirement: Plan-and-Execute 任务规划

系统 SHALL 提供 `PlanAndExecutePlanner.plan(taskDescription)` 接口，LLM 拆解任务为子步骤列表，逐步执行并支持结果回填修正计划。

#### Scenario: LLM 拆解任务

- **WHEN** 调用 `planner.plan("查询北京天气并翻译成英文")`
- **THEN** LLM 返回 JSON `{"steps":[{"desc":"query weather","skill":"web_search","input":{"q":"北京天气"}},{"desc":"translate","skill":"llm","input":{"text":"$s1.result","to":"en"}}]}`
- **AND** 逐步执行，前一步结果作为 `$s1` 引用注入下一步 input

#### Scenario: 计划修正

- **WHEN** step 1 执行结果不满足下一步前置条件（如 web_search 返回空）
- **THEN** LLM 重新规划剩余步骤，跳过依赖失败的步骤
- **AND** `agent_trace` 记录 `step_type=thought, content=replan`

#### Scenario: 无可用 Skill 时降级

- **WHEN** LLM 规划的步骤引用未注册 Skill `nonexistent_skill`
- **THEN** 抛 `SKILL_NOT_FOUND`，runtime 转 `failed`
- **AND** 不重试（避免循环失败）

### Requirement: 子任务串联

`agent_runtime.parent_runtime_id` 字段 SHALL 关联父子任务，子任务结果回传父任务 context。

#### Scenario: 创建子任务

- **WHEN** workflow 节点 n2 type=subagent，调用 `SkillRegistry.invoke` 创建子 runtime
- **THEN** 子 `agent_runtime.parent_runtime_id = <parent_runtime_id>`
- **AND** 子 runtime 独立状态机，父 runtime 状态保持 `running`（不阻塞）

#### Scenario: 子任务完成回传

- **WHEN** 子 runtime 转 `done`
- **THEN** 触发 `agent.state.notify` 事件通知父 runtime
- **AND** 父 runtime 的 `AgentContext.observations` 池写入 `<childRuntimeId>: <finalOutput>`
- **AND** 父 runtime 继续执行后续节点
