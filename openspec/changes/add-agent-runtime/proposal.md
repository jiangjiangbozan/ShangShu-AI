## Why

平台当前已具备对话、RAG、ES 全局检索等基础能力，但 Agent 工程化体系完全空白：业务侧只有 `ChatStreamService` 一条 SSE 链路，无法支撑任务型场景（多步推理、工具调用、长程任务断点续跑、跨 Agent 协作）。`docs/development-plan.md` M4 验收指标要求"任务完成率 ≥ 70%、Trace 100% 可回放、断点续跑 95% 恢复"，本 change 是该里程碑的唯一交付物。

同时 M1 补充项已为 Agent 工程化预留了 5 张数据表（`agent_runtime` / `agent_task` / `agent_trace` / `agent_context_snapshot` / `skill_definition`）与配套 Redis / RabbitMQ 资源，但实现层完全缺失，无法进入 M5（多 Agent 协作原型）。

本 change 解决：

1. **缺统一运行时**：Agent 没有显式生命周期，长程任务无法暂停 / 续跑 / 取消，重启即丢
2. **Skill 生态缺规范**：现有 ToolRegistry 仅是 Spring AI Function Calling，无版本管理、权限、多类型（API/local/kb/subagent/mcp）路由
3. **仅有 ReAct 单循环**：无法支撑配置化工作流（DAG）与 Plan-and-Execute 任务规划
4. **无可观测性**：Thought/Action/Observation 无结构化记录，无法回放调试、成本归因
5. **Token 爆炸**：长对话无压缩机制，agent 上下文窗口超限直接失败
6. **前端无 Agent 配置入口**：用户无法新建 Agent、查看运行 Trace、调试失败任务

## What Changes

### 1. Agent Runtime 引擎

- 状态机：`pending → running ↔ paused → done/failed/cancelled`
- 异步任务调度：长程任务走 `agent.task.dispatch` 队列，不阻塞 SSE 主线程
- 断点续跑：每步 `context_snapshot` 落库 + `last_heartbeat` 心跳；启动扫描 `running > ttl` 任务转 `failed`，支持 `resume`
- `AgentContextManager`：统一管理 chat history + observation 池 + thought chain，序列化为 `context_snapshot`

### 2. Skill 技能体系

- `@Skill` 注解式注册：`name / version / type / permission / endpoint`
- `SkillRegistry` 启动时扫描 Bean，注册到 `skill_definition` 表（UNIQUE(name, version)）
- 5 种 Skill 类型路由：`api`（HTTP）/ `local`（Bean#method）/ `kb`（复用 M3 RAG）/ `subagent`（M5 完善）/ `mcp`（接口预留）
- 内置 Skill：`calculator`（local）、`web_search`（api，mock 实现）、`code_executor`（Docker 沙箱）、`kb_retrieval`（复用 `RagRetrievalService`）

### 3. 工作流编排

- `workflow_definition.dag` JSON 配置：节点（skill/llm/branch）+ 边 + 条件分支
- `DagEngine`：拓扑排序执行，节点结果按 `inputs: ["$n1","$n2"]` 引用注入
- `PlanAndExecutePlanner`：LLM 拆解任务为子步骤，逐步执行，支持结果回填修正计划
- 子任务串联：`agent_runtime.parent_runtime_id` 关联父子任务，子结果回传父 context

### 4. 全链路 Trace

- 每步 `Thought / Action / Observation` 写入 `agent_trace` 表（异步，通过 `agent.trace.collect` MQ）
- `GET /api/v1/agent/runtimes/{runtimeId}/replay` 按 step_index 顺序返回链路
- 性能埋点：每步 `latency_ms / token_in / token_out`，按 runtimeId 汇总
- 异常步骤标记 `error`，关联死信队列消息 ID

### 5. 基础记忆模块

- 短期记忆：滑动窗口（默认 10 轮）+ Token 自动压缩（阈值 4K，超阈值 LLM 摘要 + 保留最近 2 轮原文）
- 压缩快照存 `agent:ctx:{runtimeId}` Redis（TTL 24h）
- `MemoryStore` 接口预留长期记忆：`save(userId, key, value)` / `recall(userId, query)`（V2 接入向量库）

### 6. 前端 Agent 配置与 Trace 可视化

- Agent 列表 / 详情 / 新建表单（绑定 modelId + skill 集合 + 工作流模板）
- 运行时列表 + 状态徽章（pending/running/paused/done/failed）
- Trace 时间轴：按 step_index 顺序展示 Thought/Action/Observation，支持折叠 / 展开 raw JSON

## Capabilities

### New Capabilities

- `agent/runtime`: Agent 运行时引擎，状态机 + 异步调度 + 断点续跑 + 上下文管理
- `agent/skill`: Skill 技能体系，注解注册 + 5 类型路由 + 版本管理 + 内置 Skill
- `agent/workflow`: 工作流编排，DAG 引擎 + Plan-and-Execute + 子任务串联
- `agent/trace`: 全链路追踪，结构化记录 + 回放 API + 性能埋点
- `agent/memory`: 基础记忆模块，短期滑动窗口 + Token 压缩 + MemoryStore 接口
- `agent/react-engine`: ReAct 推理引擎，Thought/Action/Observation 循环驱动 ReactEngine

### Modified Capabilities

无。本 change 是新增 Agent 工程化模块，不修改 chat / RAG / document 现有 spec 行为。仅作为消费方复用 M3 的 `RagRetrievalService` 与 M2 的 `ChatClientFactory`，不改变其外部接口。

## Impact

- **数据库**：5 张表迁移脚本（`V20260901__add_agent_engineering_tables.sql`），M1 补充项已规划但未实际落地，本 change 一并交付
- **后端代码**：
  - 新增 `api/src/main/java/com/ai/api/agent/` 包，子模块 `runtime` / `skill` / `workflow` / `trace` / `memory` / `react` / `controller` / `config` / `mapper` / `entity`
  - 集成点：复用 `ChatClientFactory`、`RagRetrievalService`、`RabbitMqConfig`
- **前端代码**：
  - 新增 `ai-agent-web/src/views/agent/` 目录（AgentList / AgentDetail / RuntimeList / TraceTimeline）
  - 新增 `ai-agent-web/src/api/agent.ts` 与 `ai-agent-web/src/stores/agent.ts`
- **配置**：
  - `application-dev.yml` 新增 `agent.*` 配置块（heartbeat-ttl / memory-window / memory-token-threshold / task-queue）
  - Docker Compose 新增 `agent-sandbox` 容器（code_executor 沙箱，Python 3.11 slim 镜像）
- **依赖**：无新引入 Maven 依赖（复用 Spring AI、Spring AMQP、Spring Data Elasticsearch）
- **关联 change**：
  - 依赖 `add-chat-module`（ChatClientFactory 路由）
  - 依赖 `add-p3-rag-feedback-ops`（RagRetrievalService 复用）
  - 阻塞 M5 `add-multi-agent-orchestration`（subagent Skill 需要 Runtime 先行）

## 非目标

- **不做** subagent 多 Agent 协作（M5 `add-multi-agent-orchestration` 范围）
- **不做** MCP 协议外部工具接入（仅预留接口与 type 枚举）
- **不做** Skill 调用 RBAC 完整权限模型（M6 `add-system-management` 范围），本 change 仅记录 `permission_tag` 字段不校验
- **不做** 评测体系（recall@k / step_accuracy / 自动化评测流水线，M6 `add-agent-eval-framework` 范围）
- **不做** 长期记忆向量库接入（仅定义 `MemoryStore` 接口，V2 落地）
- **不做** 完整工作流可视化编辑器（仅支持 JSON 配置 + 时间轴展示，DAG 拖拽编辑留 V2）
- **不做** Skill 热加载 / 卸载（启动时扫描注册，运行期不可变，热加载留 V2）

## 验收标准

1. 启动后 `skill_definition` 表自动注册 ≥ 4 个内置 Skill（calculator / web_search / code_executor / kb_retrieval），UNIQUE(name, version) 约束生效
2. `POST /api/v1/agent/runtimes` 创建 runtime 后状态为 `pending`，调用 `start` 转 `running`，`pause` 转 `paused`，`resume` 回 `running`
3. ReactEngine 执行 3 步内任务（如"查询天气并翻译"）完成率 ≥ 70%，每步写入 `agent_trace`
4. `GET /api/v1/agent/runtimes/{id}/replay` 返回按 step_index 升序的完整链路，100% 步骤可回放
5. 应用重启后扫描 `last_heartbeat` 超 TTL 的 `running` 任务转 `failed`，调用 `resume` 接口 95% 任务可从 `context_snapshot` 恢复
6. 短期记忆窗口达 4K token 时触发 LLM 压缩，压缩后 `agent:ctx:{runtimeId}` Redis Key 写入压缩快照
7. `code_executor` Skill 在独立 Docker 容器内执行 Python 代码，主进程崩溃不影响应用
8. 前端 Agent 列表 / 详情 / Trace 时间轴三个页面可访问，Trace 支持 step 折叠 / 展开 raw JSON
9. 异常步骤 `agent_trace.error` 字段标记，关联 `agent.task.dlq` 死信消息 ID
10. 工作流 DAG JSON 配置 3 节点（web_search + kb_retrieval + llm 总结）能按拓扑序执行并汇总结果
