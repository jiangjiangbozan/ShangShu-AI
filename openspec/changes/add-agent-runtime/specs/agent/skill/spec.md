## Purpose

Skill 技能体系实现注解式注册、5 类型路由、版本管理与内置 Skill 集合，替代原"注解式 ToolRegistry"方案。

## ADDED Requirements

### Requirement: @Skill 注解式注册

系统 SHALL 提供 `@Skill` 注解，标注在 Spring Bean 方法上，启动时由 `SkillRegistry` 扫描注册到 `skill_definition` 表。

`@Skill` 字段：

- `name`：技能名（kebab-case，UNIQUE 约束组成部分）
- `version`：语义版本号（默认 `1.0.0`，UNIQUE(name, version) 约束）
- `type`：SkillType 枚举（`api | local | kb | subagent | mcp`）
- `permission`：权限标签（默认 `""`，M6 RBAC 校验使用）
- `description`：技能描述（供 LLM 工具选择 prompt 使用）

#### Scenario: 启动时自动注册

- **GIVEN** Bean `CalculatorSkill` 方法 `add` 标注 `@Skill(name="calculator", type=SkillType.LOCAL)`
- **WHEN** 应用启动
- **THEN** `skill_definition` 表写入或更新一条记录，`enabled=true`
- **AND** 日志输出 `[SkillRegistry][registered skill: calculator#1.0.0 type=LOCAL]`

#### Scenario: 重复注册幂等

- **WHEN** 同一 Skill 名+版本第二次启动注册
- **THEN** 走 `INSERT ... ON DUPLICATE KEY UPDATE`，更新 `endpoint / input_schema / output_schema`
- **AND** 不抛唯一约束冲突

#### Scenario: 调用未注册 Skill

- **WHEN** ReactEngine 调用 `SkillRegistry.invoke("nonexistent", "1.0.0", input)`
- **THEN** 抛 `BusinessException(ErrorCode.SKILL_NOT_FOUND, "skill not registered: nonexistent")`

### Requirement: 5 种 Skill 类型路由

`SkillRegistry.invoke(skillName, version, input)` SHALL 按 `type` 字段路由到不同的执行器：

| type | 执行方式 | 输入 | 输出 |
|------|---------|------|------|
| `local` | 反射调用 Bean#method | JSON | JSON |
| `api` | HTTP POST 到 `endpoint` | JSON body | JSON response |
| `kb` | 调用 `RagRetrievalService.retrieve(query, kbId)` | `{"query":"...","kbId":1}` | `{"chunks":[...]}` |
| `subagent` | 创建子 `AgentRuntime`（M5 完善，本 change 仅占位） | runtime 输入 | runtime 最终输出 |
| `mcp` | MCP 协议调用外部 server（接口预留，本 change 不实现） | MCP 请求 | MCP 响应 |

#### Scenario: local 类型调用

- **WHEN** 调用 `calculator` Skill（type=local），input=`{"expr":"1+2"}`
- **THEN** 反射调用 `CalculatorSkill.eval("1+2")`
- **AND** 返回 `{"result":3}`

#### Scenario: api 类型调用失败

- **WHEN** 调用 `web_search` Skill（type=api，endpoint 指向外部 API）
- **AND** 外部 API 返回 5xx
- **THEN** 抛 `BusinessException(ErrorCode.SKILL_INVOKE_FAILED, "api skill 5xx")`
- **AND** 异常被 ReactEngine 捕获，写入 `agent_trace.error`

#### Scenario: kb 类型路由到 RAG

- **WHEN** 调用 `kb_retrieval` Skill（type=kb），input=`{"query":"产品规格","kbId":2}`
- **THEN** 调用 `RagRetrievalService.retrieve("产品规格", 2L)`
- **AND** 返回 `{"chunks":[{"content":"...","score":0.87}]}`
- **AND** 命中计数 +1（复用 M3 反馈闭环）

#### Scenario: subagent 类型返回 NOT_IMPLEMENTED

- **WHEN** 调用 type=subagent 的 Skill
- **THEN** 抛 `BusinessException(ErrorCode.SKILL_NOT_IMPLEMENTED, "subagent skill not supported in M4")`

#### Scenario: mcp 类型返回 NOT_IMPLEMENTED

- **WHEN** 调用 type=mcp 的 Skill
- **THEN** 抛 `BusinessException(ErrorCode.SKILL_NOT_IMPLEMENTED, "mcp skill not supported in M4")`

### Requirement: 版本管理

调用方 SHALL 支持显式版本锁定或 `latest` 解析：

#### Scenario: 显式版本调用

- **WHEN** 调用 `SkillRegistry.invoke("calculator", "1.0.0", input)`
- **THEN** 查询 `skill_definition WHERE name='calculator' AND version='1.0.0'`
- **AND** 未找到则抛 `SKILL_VERSION_NOT_FOUND`

#### Scenario: latest 版本解析

- **WHEN** 调用 `SkillRegistry.invoke("calculator", "latest", input)`
- **THEN** 查询 `WHERE name='calculator' ORDER BY version DESC LIMIT 1`
- **AND** 返回最高版本的 Skill 执行

### Requirement: Skill 调用超时护栏

`SkillInvoker.invoke` SHALL 对所有 Skill 调用包一层 `CompletableFuture.supplyAsync(...).orTimeout(timeoutSeconds, SECONDS).join()` 超时护栏（add-skill-timeout-guard）：

- 超时时长取 `agent.react.skill-timeout`（默认 30s，秒）
- 超时**不抛异常**，返回 `{"error":"skill timeout"}` 作为观察结果
- 非超时的真实失败（`BusinessException`）仍按原逻辑抛出

`ReactEngine` SHALL 在检测到 `{"error":"skill timeout"}` observation 后：

- 停止继续调用任何工具
- 调用 LLM 使用专用收尾 prompt（不允许输出 action，只允许 `final_answer`），基于已收集的全部 Observation 生成最终答案
- LLM 收尾失败时降级返回固定提示文本

#### Scenario: Skill 调用超时返回错误观察

- **WHEN** 调用 `web_search` Skill 且执行超过 `agent.react.skill-timeout`（默认 30s）
- **THEN** `SkillInvoker.invoke` 返回 `{"error":"skill timeout"}`
- **AND** 不抛 `BusinessException`

#### Scenario: 超时后引擎基于已有观察收尾

- **GIVEN** ReAct 已执行 2 步成功收集 2 条 observation
- **WHEN** 第 3 步 Skill 调用返回 `{"error":"skill timeout"}`
- **THEN** ReactEngine 停止继续调用工具
- **AND** 调用 LLM（收尾 prompt：不再允许 action）生成 `final_answer`
- **AND** runtime 状态置 `completed`，finalAnswer 为 LLM 基于已有 observation 的答案
- **AND** trace 记录 `skill timeout` 错误

#### Scenario: 超时收尾 LLM 失败降级

- **WHEN** 超时后收尾 LLM 调用抛异常
- **THEN** runtime 状态置 `completed`，finalAnswer 为固定提示"部分工具调用超时，基于已有信息仍无法给出完整回答，请稍后重试。"

### Requirement: 内置 Skill 集合

本 change SHALL 交付 4 个内置 Skill，启动时自动注册：

| name | type | 实现 | 验证 |
|------|------|------|------|
| `calculator` | local | `CalculatorSkill.eval(expr)`，支持四则运算 | `1+2*3` → 7 |
| `web_search` | api | mock 实现，返回固定结果 `{"results":[{"title":"mock","url":"..."}]}`，不实际调用外部 API | 返回非空 results 数组 |
| `code_executor` | local（Docker 沙箱） | `CodeExecutorSkill.execute(code, lang)`，通过 Docker API 在 `agent-sandbox` 容器内执行 Python 代码 | `print("hello")` → `"hello\n"` |
| `kb_retrieval` | kb | 复用 `RagRetrievalService.retrieve(query, kbId)` | 命中返回 chunks 数组 |

#### Scenario: code_executor 沙箱隔离

- **WHEN** 调用 `code_executor` Skill 执行 `import os; os.system("rm -rf /")`
- **THEN** 在 `agent-sandbox` 容器内执行，主应用进程不受影响
- **AND** 容器以只读 rootfs + no-new-privileges 启动，操作失败返回非零退出码
- **AND** 执行超时（默认 10s）由 Docker API 强制 kill 容器进程
