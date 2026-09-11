## Purpose

ReAct 推理引擎实现 Thought/Action/Observation 循环，驱动 Agent 完成多步任务，覆盖 M4 验收指标"任务完成率 ≥ 70%"。

## ADDED Requirements

### Requirement: ReAct 循环

`ReactEngine` SHALL 实现 Thought → Action → Observation 循环，最大迭代次数 `agent.react.max-iterations=10`，超出转 `failed`。

循环步骤：

1. **Thought**：LLM 接收 `chat history + observations + user query`，输出 JSON `{"thought":"...","action":"skill_name","input":{...}}` 或 `{"thought":"...","final_answer":"..."}`
2. **Action**：若 LLM 输出含 `action`，调用 `SkillRegistry.invoke(action, input)`
3. **Observation**：把 Skill 返回结果写入 `AgentContext.observations` 池
4. **回 1**：再次调用 LLM，带上一步 observation
5. **终态**：LLM 输出 `final_answer` 时结束，转 `done`

每步 MUST 写 `agent_trace` 记录，并调用 `AgentContextManager.snapshot()` 持久化上下文。

#### Scenario: 简单 3 步任务

- **GIVEN** user query = "查询北京天气并翻译成英文"
- **WHEN** `POST /api/v1/agent/runtimes` 创建 runtime + `start`
- **THEN** step 0 (thought): LLM 输出 `{"action":"web_search","input":{"q":"北京天气"}}`
- **AND** step 1 (action): 调用 web_search Skill，返回 `{"results":[{"title":"..."}]}`
- **AND** step 2 (thought): LLM 输出 `{"final_answer":"Beijing weather: ..."}`
- **AND** runtime 转 `done`，返回 final_answer

#### Scenario: 达到最大迭代

- **GIVEN** `agent.react.max-iterations=10`
- **WHEN** LLM 持续输出 action 不收敛，达到 10 次
- **THEN** runtime 转 `failed`，`error_message=max iterations reached`
- **AND** 最后一步 `agent_trace.error` 标记

#### Scenario: Skill 调用失败降级

- **WHEN** step 2 action 调用 `web_search` 抛 `SKILL_INVOKE_FAILED`
- **THEN** ReactEngine 把异常作为 observation 写入 `{"error":"..."}`
- **AND** 继续下一轮 thought，LLM 决定换 Skill 或输出 final_answer
- **AND** 不立即转 `failed`，给 LLM 自愈机会

#### Scenario: LLM 输出非法 JSON

- **WHEN** LLM 返回非 JSON 或字段缺失
- **THEN** 重试一次，prompt 加 `请严格输出 JSON`
- **AND** 仍失败则 runtime 转 `failed`，`error_message=invalid llm output`

### Requirement: Prompt 模板

ReactEngine SHALL 使用结构化 prompt，包含 system + few-shot 示例：

```
You are an Agent that solves tasks step-by-step using ReAct pattern.
Available skills: {skill_descriptions}

Output strict JSON only:
{"thought":"your reasoning","action":"skill_name","input":{...}}
or
{"thought":"your reasoning","final_answer":"..."}

Previous observations: {observations}
User query: {user_query}
```

#### Scenario: skill_descriptions 注入

- **WHEN** ReactEngine 调用 LLM
- **THEN** prompt 中 `{skill_descriptions}` 替换为 `skill_definition` 表所有 enabled=true 的 Skill 描述列表
- **AND** 每个 Skill 含 `name + version + description + input_schema`

#### Scenario: observations 池注入

- **WHEN** step 2 调用 LLM
- **THEN** prompt 中 `{observations}` 替换为前序所有 observation 的 JSON 数组
- **AND** 数组大小超过 5 时只保留最近 5 条（避免 prompt 爆炸）

### Requirement: 与现有 ChatStreamService 集成

ReactEngine SHALL 复用 `ChatClientFactory.resolveChatClient(modelId)`，禁止直接 new ChatClient。

#### Scenario: modelId 路由

- **WHEN** runtime 关联的 `agentConfig.modelId="deepseek"`
- **THEN** ReactEngine 调用 `chatClientFactory.getChatClient("deepseek")` 获取 ChatClient
- **AND** 复用与 ChatStreamService 相同的 modelId 路由逻辑
