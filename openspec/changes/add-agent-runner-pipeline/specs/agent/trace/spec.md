## Purpose

思维链持久化与重建查询：对标 Dify `MessageAgentThought` + `MessageChain`，基于 `agent_trace` 表封装思维链重建能力——按 `trace_id`（runtimeId）完整还原 Thought→Action→Observation 推理过程，支持分页获取，供前端展示与调试回放。

## ADDED Requirements

### Requirement: 思维链完整重建

`TraceService.rebuildChain(runtimeId)` SHALL 按 `step_index` 有序聚合该 runtime 全部 `agent_trace` 记录，还原完整推理过程，返回结构化思维链数组：

- 每个 step SHALL 合并同一步的 thought / action / observation / error / resume 记录
- `thought` 详情含 input（prompt）/ output（LLM 响应）/ tokenIn / tokenOut / latencyMs
- `action` 详情含 skillId / input / output / latencyMs
- `observation` 为 Skill 输出的解析结果
- 返回数组 MUST 按 step_index 升序

#### Scenario: 完整还原推理过程

- **GIVEN** runtime 存在 step 0（thought+action+observation）与 step 1（thought+final）
- **WHEN** `rebuildChain(runtimeId)`
- **THEN** 返回 2 条结构化记录，step 0 聚合 thought/action/observation 三个字段
- **AND** thought 含 prompt/llm 响应/token/耗时，action 含 skillId/入参/出参

#### Scenario: 解析容错

- **WHEN** `agent_trace` 某字段 JSON 非法（如 output 为纯文本）
- **THEN** 该字段原样透出文本，不抛异常

### Requirement: 思维链分页

`TraceService.rebuildChain(runtimeId, page, size)` SHALL 支持分页获取思维链步骤：

- `page` 从 1 开始，`size` ≥ 1；参数非法 MUST 归一为合法值
- 越界页 MUST 返回空数组

#### Scenario: 分页获取

- **GIVEN** 思维链共 5 步，调用 `rebuildChain(runtimeId, 2, 2)`
- **THEN** 返回第 3、4 步
- **AND** 调用 `rebuildChain(runtimeId, 99, 10)` 返回空数组

### Requirement: Chain 查询接口

系统 SHALL 提供 `GET /api/v1/agent/trace/{runtimeId}/chain` 接口：

- 不带 `page`/`size` 参数时返回完整思维链
- 带 `page`/`size` 时返回分页结果
- 统一 `Result` 包装

#### Scenario: 前端调试回放

- **WHEN** 前端调用 `GET /api/v1/agent/trace/{runtimeId}/chain`
- **THEN** 返回 `{"code":0,"data":[{step,thought,action,observation,...}]}` 结构化数组
- **AND** 支持 `?page=1&size=10` 分页
