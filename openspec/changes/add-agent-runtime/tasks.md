# 实施任务 - add-agent-runtime

> 任务粒度 ≤ 4 小时；按数据层 → 实体/Mapper → 能力层 → 业务层 → 接入层顺序执行；每个任务标注验证方式。
>
> 状态：实施中

## 阶段 1: 数据层与配置

### Task 1.1: 数据库迁移脚本

- [ ] 新增 `api/src/main/resources/db/migration/V20260901__add_agent_engineering_tables.sql`
- [ ] 创建 5 张表：`agent_runtime` / `agent_task` / `agent_trace` / `skill_definition` / `workflow_definition`
- [ ] 索引：`idx_session_status` / `idx_heartbeat` / `uk_runtime_step` / `uk_name_version` / `uk_workflow_version`
- **验证**：应用启动 Flyway 自动迁移；`SHOW TABLES LIKE 'agent_%'` 返回 5 行

### Task 1.2: AgentProperties 配置

- [ ] 新增 `com.ai.api.agent.config.AgentProperties`（`@ConfigurationProperties(prefix="agent")`）
- [ ] 字段：`heartbeat-ttl` / `react.max-iterations` / `memory.window-size` / `memory.token-threshold` / `workflow.node-timeout` / `mq.*`
- [ ] `application-dev.yml` 新增 `agent:` 配置块（含默认值）
- **验证**：`mvn compile` 通过；`@SpringBootTest` 注入 `AgentProperties` 字段非 null

### Task 1.3: AgentMqConfig 队列声明

- [ ] 新增 `com.ai.api.agent.config.AgentMqConfig`
- [ ] 声明 3 个队列：`agent.task.dispatch` / `agent.state.notify` / `agent.trace.collect`
- [ ] 声明 2 个 DLX：`agent.task.dlq` / `agent.trace.dlq`
- [ ] 声明 2 个 exchange：`agent.task`（topic）/ `agent.state`（fanout）/ `agent.trace`（topic）
- **验证**：应用启动 RabbitMQ 自动创建队列；管理界面可见 3 队列 + 2 DLX

## 阶段 2: 实体与 Mapper 层

### Task 2.1: 5 个实体类

- [ ] 新增 `com.ai.api.agent.entity.AgentRuntime`（@TableName("agent_runtime")，字段对应表结构）
- [ ] 新增 `com.ai.api.agent.entity.AgentTask`
- [ ] 新增 `com.ai.api.agent.entity.AgentTrace`
- [ ] 新增 `com.ai.api.agent.entity.SkillDefinition`
- [ ] 新增 `com.ai.api.agent.entity.WorkflowDefinition`
- [ ] JSON 字段使用 `@TableField(typeHandler = JacksonTypeHandler.class)` 反序列化
- **验证**：`mvn compile` 通过；字段类型与表结构一致

### Task 2.2: 5 个 Mapper

- [ ] 新增 `AgentRuntimeMapper extends BaseMapper<AgentRuntime>`
- [ ] 自定义方法：`selectOverdueRunning(LocalDateTime cutoff)` 返回超时任务列表
- [ ] 新增 `AgentTaskMapper` / `AgentTraceMapper` / `SkillDefinitionMapper` / `WorkflowDefinitionMapper`
- [ ] `SkillDefinitionMapper` 自定义：`selectLatestByName(String name)` ORDER BY version DESC
- **验证**：`@MybatisTest` 注入 Mapper，`SELECT 1` 成功

## 阶段 3: Skill 体系（模块 2）

### Task 3.1: @Skill 注解 + SkillType 枚举

- [ ] 新增 `com.ai.api.agent.skill.Skill` 注解（`@Target(METHOD)` `@Retention(RUNTIME)`）
- [ ] 字段：`name` / `version` 默认 `1.0.0` / `type` / `permission` 默认 `""` / `description` 默认 `""`
- [ ] 新增 `SkillType` 枚举（`API / LOCAL / KB / SUBAGENT / MCP`）
- **验证**：注解可用，编译通过

### Task 3.2: SkillRegistry + SkillInvoker

- [ ] 新增 `SkillRegistry`：`register()` 方法扫描 `@Skill`，upsert 到 `skill_definition` 表
- [ ] `invoke(name, version, input)`：`latest` 解析最高版本，路由到 `SkillInvoker`
- [ ] 新增 `SkillInvoker`：按 type 分发
  - `LOCAL`：反射调用 Bean#method
  - `API`：`RestTemplate.postForEntity(endpoint, input, Map.class)`
  - `KB`：调用 `RagRetrievalService.retrieve`
  - `SUBAGENT` / `MCP`：抛 `SKILL_NOT_IMPLEMENTED`
- **验证**：单测 mock `BeanFactory`，调用 `calculator` Skill 返回 `{"result":3}`

### Task 3.3: SkillInitializer 启动注册

- [ ] 新增 `SkillInitializer implements ApplicationRunner`
- [ ] `run()` 调用 `SkillRegistry.register()` 扫描所有 `@Skill` 注解
- [ ] 日志输出 `[SkillRegistry][registered skill: {name}#{version} type={type}]`
- **验证**：应用启动日志可见 4 个内置 Skill 注册成功

### Task 3.4: 内置 Skill - CalculatorSkill

- [ ] 新增 `com.ai.api.agent.skill.builtin.CalculatorSkill`
- [ ] `@Skill(name="calculator", type=SkillType.LOCAL, description="四则运算")` 标注方法 `eval(String expr)`
- [ ] 用 `javax.script.ScriptEngine`（Nashorn 替代 ScriptEngineManager）或自实现递归下降解析器
- [ ] 返回 `{"result":<double>}`
- **验证**：`SkillRegistry.invoke("calculator", "latest", {"expr":"1+2*3"})` 返回 `{"result":7}`

### Task 3.5: 内置 Skill - WebSearchSkill（mock）

- [ ] 新增 `WebSearchSkill`
- [ ] `@Skill(name="web_search", type=SkillType.API, endpoint="mock")` 标注
- [ ] mock 实现：返回固定 `{"results":[{"title":"mock result","url":"https://example.com/mock","snippet":"This is a mock search result."}]}`
- [ ] 不实际调用外部 API（避免外部依赖）
- **验证**：调用返回非空 results 数组

### Task 3.6: 内置 Skill - CodeExecutorSkill（Docker 沙箱）

- [ ] 新增 `CodeExecutorSkill`
- [ ] `@Skill(name="code_executor", type=SkillType.LOCAL, description="Python 沙箱执行")`
- [ ] 通过 `com.github.docker-java:docker-java` 客户端创建容器 `python:3.11-slim`
- [ ] 挂载代码到 `/tmp/code.py`，`--read-only --security-opt no-new-privileges --network=none`
- [ ] 超时 10s 由 Docker API 强制 kill
- [ ] 收集 stdout/stderr 返回 `{"stdout":<str>,"stderr":<str>,"exitCode":<int>}`
- [ ] `pom.xml` 新增 `com.github.docker-java:docker-java-core` 依赖
- [ ] `docker-compose.yml` 新增 `agent-sandbox` 服务
- **验证**：调用 `print("hello")` 返回 `{"stdout":"hello\n","exitCode":0}`；调用 `os.system("rm -rf /")` 主进程未受影响

### Task 3.7: 内置 Skill - KbRetrievalSkill

- [ ] 新增 `KbRetrievalSkill`
- [ ] `@Skill(name="kb_retrieval", type=SkillType.KB, description="知识库检索")`
- [ ] 注入 `RagRetrievalService`，调用 `retrieve(query, kbId)`
- [ ] 返回 `{"chunks":[{"content":...,"score":...}]}`
- **验证**：mock `RagRetrievalService`，调用返回 chunks 数组

### Task 3.8: Skill 单元测试

- [ ] 新增 `SkillRegistryTest`：覆盖 `register / invoke / latest 解析 / SKILL_NOT_FOUND / SKILL_NOT_IMPLEMENTED`
- [ ] 新增 `CalculatorSkillTest` / `CodeExecutorSkillTest`
- **验证**：`mvn test` 全部通过

## 阶段 4: Agent Runtime 引擎（模块 1）

### Task 4.1: AgentStatus 枚举 + 状态机

- [ ] 新增 `AgentStatus` 枚举（`PENDING / RUNNING / PAUSED / DONE / FAILED / CANCELLED`）
- [ ] 在 `AgentRuntimeService` 定义 `Map<AgentStatus, Set<AgentStatus>>` 合法转移表
- [ ] `transition(from, to)` 方法校验合法性，非法抛 `AGENT_INVALID_STATE_TRANSITION`
- **验证**：单测覆盖所有合法 + 非法转移

### Task 4.2: AgentContext + AgentContextManager

- [ ] 新增 `AgentContext`：`chatHistory` / `observations Map<String,Object>` / `thoughtChain List` / `nextStepIndex`
- [ ] 新增 `AgentContextManager`
  - `load(runtimeId)`：先查 Redis `agent:ctx:{runtimeId}`，未命中查 MySQL `context_snapshot` 字段
  - `snapshot(runtimeId, context)`：序列化为 JSON，双写 Redis + MySQL
  - `restore(runtimeId)`：从 JSON 反序列化
- **验证**：单测覆盖 load / snapshot / restore 往返一致

### Task 4.3: AgentRuntimeService 核心方法

- [ ] 新增 `AgentRuntimeService`
- [ ] `createRuntime(sessionId, agentConfigId, modelId, userId)`：插入 `agent_runtime` 表，返回 runtimeId
- [ ] `start(runtimeId)`：状态 `pending → running`，向 `agent.task.dispatch` 发送消息
- [ ] `pause(runtimeId)`：状态 `running → paused`，更新 `next_step_index`
- [ ] `resume(runtimeId)`：状态 `paused → running`，从 `context_snapshot` 恢复 AgentContext，重新派发任务
- [ ] `cancel(runtimeId)`：状态 `running | paused → cancelled`
- [ ] `fail(runtimeId, errorMessage, dlqMessageId)`：状态 `running → failed`
- [ ] `done(runtimeId, finalAnswer)`：状态 `running → done`
- [ ] `heartbeat(runtimeId)`：更新 `last_heartbeat`
- **验证**：单测 mock Mapper + MQ，覆盖所有状态转移

### Task 4.4: AgentTaskConsumer（MQ 消费者）

- [ ] 新增 `AgentTaskConsumer` `@RabbitListener(queues="${agent.mq.task-dispatch-queue}")`
- [ ] 消费 `{"runtimeId":<id>}` 消息
- [ ] 调用 `ReactEngine.execute(runtimeId)` 同步执行
- [ ] 异常进入 `agent.task.dlq`，调用 `AgentRuntimeService.fail()`
- [ ] 执行前用 Redis 分布式锁 `agent:lock:{runtimeId}` 防双跑
- **验证**：集成测试用 Testcontainers，发送 MQ 消息触发消费

### Task 4.5: HeartbeatScanner（定时扫描）

- [ ] 新增 `HeartbeatScanner` `@Scheduled(fixedDelay=60000)`
- [ ] 调用 `AgentRuntimeMapper.selectOverdueRunning(now() - ttl)` 查询超时任务
- [ ] 状态转 `failed`，`error_message=heartbeat timeout`
- [ ] 触发 `agent.state.notify` 事件
- **验证**：单测模拟 `last_heartbeat` 旧时间，扫描后状态转 `failed`

### Task 4.6: AgentStateNotifier（状态变更通知）

- [ ] 新增 `AgentStateNotifier` `@Async`
- [ ] `notify(runtimeId, oldStatus, newStatus)` 向 `agent.state.notify` 队列发送消息
- [ ] 消费者由前端 SSE 网关订阅（本 change 暂不实现 SSE 网关，仅发消息）
- **验证**：单测验证消息发送成功

## 阶段 5: ReAct 引擎（模块 6）

### Task 5.1: ReactPromptBuilder

- [ ] 新增 `ReactPromptBuilder`
- [ ] `build(chatHistory, observations, skillDescriptions, userQuery)` 返回完整 prompt 字符串
- [ ] system prompt 包含 Skill 列表 + 输出 JSON 格式约束
- [ ] few-shot 示例：1 个 web_search 例子
- **验证**：单测验证 prompt 含 4 个内置 Skill 描述

### Task 5.2: ReactOutputParser

- [ ] 新增 `ReactOutputParser`
- [ ] `parse(llmOutput)` 返回 `ReactOutput`（含 `thought` / `action?` / `input?` / `finalAnswer?`）
- [ ] 非 JSON / 字段缺失抛 `IllegalArgumentException`
- **验证**：单测覆盖正常输出 / final_answer / 非法 JSON 三种情况

### Task 5.3: ReactEngine 主循环

- [ ] 新增 `ReactEngine`
- [ ] 注入 `ChatClientFactory` / `SkillRegistry` / `AgentContextManager` / `MemoryStore` / `TraceService`
- [ ] `execute(runtimeId)` 循环：
  1. 加载 `AgentContext`
  2. 调用 `ChatClient.call(prompt)`
  3. 解析输出
  4. 若 `final_answer` → 调用 `AgentRuntimeService.done()`
  5. 若 `action` → 调用 `SkillRegistry.invoke()`，写入 observation
  6. 写 trace + 调用 `AgentContextManager.snapshot()`
  7. `max-iterations` 超出 → `fail()`
- **验证**：集成测试 mock ChatClient 返回固定输出，3 步完成简单任务

### Task 5.4: ReactEngine 异常处理

- [x] Skill 调用失败：异常作为 observation 写入 `{"error":"..."}`（超时护栏 add-skill-timeout-guard：超时返回 `{"error":"skill timeout"}`，引擎停止调工具、基于已有 observation 收尾回答）
- [ ] LLM 输出非法 JSON：重试 1 次，仍失败 `fail()`
- [ ] 重试 prompt 加 `请严格输出 JSON 格式`
- **验证**：单测覆盖异常路径

## 阶段 6: 全链路 Trace（模块 4）

### Task 6.1: TraceService

- [ ] 新增 `TraceService`
- [ ] `recordAsync(traceDto)`：发送到 `agent.trace.collect` 队列
- [ ] `getReplay(runtimeId)`：查 `agent_trace WHERE runtime_id=? ORDER BY step_index ASC`，递归查询子任务
- [ ] `getMetrics(runtimeId)`：聚合 `latency_ms / token_in / token_out`，按 step_type + skillId 统计
- **验证**：单测 mock Mapper，覆盖聚合逻辑

### Task 6.2: TraceCollector（MQ 消费者）

- [ ] 新增 `TraceCollector` `@RabbitListener(queues="${agent.mq.trace-collect-queue}")`
- [ ] 批量入库（每 100 条或 1 秒 flush）
- [ ] 异常进入 `agent.trace.dlq`
- **验证**：集成测试发送 100 条消息，全部入库

### Task 6.3: TraceController

- [ ] 新增 `TraceController`
- [ ] `GET /api/v1/agent/runtimes/{id}/replay` 返回完整链路
- [ ] `GET /api/v1/agent/runtimes/{id}/metrics` 返回成本汇总
- [ ] runtime 不存在返回 40401
- **验证**：curl 调用接口返回 `{"code":0,"data":{...}}`

## 阶段 7: 工作流编排（模块 3）

### Task 7.1: DagEngine 拓扑排序 + 执行

- [ ] 新增 `DagEngine`
- [ ] `execute(workflow, input)`：拓扑排序，并行执行无依赖节点
- [ ] 节点 `inputs: ["$n1","$n2"]` 引用解析为前序节点输出
- [ ] 节点超时 `agent.workflow.node-timeout=60s` 转 `failed`
- [ ] DAG 校验：未知节点引用 / 环检测
- **验证**：单测 mock SkillRegistry，3 节点 DAG 执行成功

### Task 7.2: PlanAndExecutePlanner

- [ ] 新增 `PlanAndExecutePlanner`
- [ ] `plan(taskDescription)`：LLM 输出 JSON 子步骤列表
- [ ] `execute(plan)`：逐步执行，前一步结果作为 `$s1` 引用注入下一步
- [ ] 失败重规划：步骤 Skill 不存在 → 重新调 LLM 规划剩余步骤
- **验证**：单测 mock ChatClient 返回固定计划，逐步执行成功

### Task 7.3: WorkflowService + Controller

- [ ] 新增 `WorkflowService`
- [ ] `createWorkflow(dto)` / `getWorkflow(id)` / `listWorkflows()`
- [ ] `executeWorkflow(workflowId, input)`：调用 `DagEngine.execute`
- [ ] 新增 `WorkflowController`
  - `POST /api/v1/agent/workflows`
  - `GET /api/v1/agent/workflows`
  - `POST /api/v1/agent/workflows/{id}/execute`
- **验证**：curl 创建 workflow 并执行返回结果

## 阶段 8: 记忆模块（模块 5）

### Task 8.1: MemoryStore 接口 + ShortTermMemoryStore

- [ ] 新增 `MemoryStore` 接口（`load / save / compress / saveLongTerm / recallLongTerm`）
- [ ] 新增 `ShortTermMemoryStore implements MemoryStore`
- [ ] `load(runtimeId)`：从 `AgentContext.chatHistory` 取消息，超窗口触发 `compress()`
- [ ] `save(runtimeId, messages)`：更新 context + 双写 Redis/MySQL
- [ ] `compress(runtimeId)`：调 LLM 摘要前 N-2 轮，保留最近 2 轮原文
- [ ] `saveLongTerm` / `recallLongTerm` 抛 `UnsupportedOperationException`
- **验证**：单测覆盖 5 轮 / 13 轮 / token 超阈值三种情况

### Task 8.2: MemoryCompressor

- [ ] 新增 `MemoryCompressor`
- [ ] `compress(messages)`：调 ChatClient 摘要
- [ ] prompt 模板：`请用 200 字以内总结以下对话历史：{messages}`
- [ ] 返回 `Message(summary, ASSISTANT)`
- **验证**：单测 mock ChatClient 返回固定摘要

## 阶段 9: Controller 层

### Task 9.1: AgentRuntimeController

- [ ] 新增 `AgentRuntimeController`
- [ ] 8 个接口（CRUD + start/pause/resume/cancel）
- [ ] 统一 `Result<T>` 返回格式
- [ ] 参数校验：`@Valid` 校验 DTO
- **验证**：curl 创建 runtime + start，状态转 `running`

### Task 9.2: SkillController + 其他

- [ ] 新增 `SkillController`（`GET /skills` / `GET /skills/{name}`）
- [ ] 复用 `WorkflowController`（Task 7.3 已完成）
- [ ] 复用 `TraceController`（Task 6.3 已完成）
- **验证**：curl 调用列表接口返回 4 个内置 Skill

## 阶段 10: 前端 Agent 模块

### Task 10.1: API + Store + 路由

- [ ] 新增 `ai-agent-web/src/api/agent.ts`（封装所有 Agent 接口）
- [ ] 新增 `ai-agent-web/src/stores/agent.ts`（Pinia store：runtimes / skills / currentRuntime）
- [ ] 新增路由 `/agent/list` / `/agent/detail/:id` / `/agent/runtime` / `/agent/trace/:runtimeId`
- **验证**：浏览器访问 `/agent/list` 列表页正常渲染

### Task 10.2: AgentListView + AgentDetailView

- [ ] 新增 `AgentListView.vue`：表格列出所有 Agent，状态徽章 + 新建按钮
- [ ] 新增 `AgentDetailView.vue`：Agent 配置（modelId + Skill 多选 + 工作流模板）
- [ ] 新增 `StatusBadge.vue` 组件（颜色映射 6 种状态）
- **验证**：手动测试新建 Agent + 保存配置

### Task 10.3: RuntimeListView

- [ ] 新增 `RuntimeListView.vue`：runtime 列表 + 状态徽章 + 操作按钮（start/pause/resume/cancel）
- [ ] 操作按钮根据当前状态动态显示
- [ ] 点击 runtime 跳转 TraceTimelineView
- **验证**：手动测试操作按钮状态切换

### Task 10.4: TraceTimelineView + 时间轴组件

- [ ] 新增 `TraceTimelineView.vue`：调用 `/replay` 接口展示时间轴
- [ ] 新增 `TraceStep.vue`：单步卡片（step_index + step_type + content）
- [ ] 新增 `TraceStepRaw.vue`：raw JSON 折叠面板
- [ ] 子任务链路递归展示
- **验证**：手动测试折叠 / 展开功能

## 阶段 11: 测试与验收

### Task 11.1: 单元测试补全

- [ ] `AgentRuntimeServiceTest`：状态机所有合法 + 非法转移
- [ ] `ReactEngineTest`：3 步任务完成 + max-iterations 异常
- [ ] `DagEngineTest`：3 节点 DAG + 环检测 + 未知节点
- [ ] `ShortTermMemoryStoreTest`：窗口 / token 阈值压缩
- [ ] `TraceServiceTest`：聚合 metrics 计算
- **验证**：`mvn test` 覆盖率 ≥ 70%

### Task 11.2: 集成测试

- [ ] 新增 `AgentIntegrationTest` `@SpringBootTest` + Testcontainers
- [ ] 覆盖：创建 runtime → start → 3 步任务完成 → replay 链路完整
- [ ] 覆盖：pause → 应用重启模拟 → resume 续跑成功
- [ ] 覆盖：HeartbeatScanner 超时转 failed
- **验证**：测试全部通过

### Task 11.3: 验收脚本

- [ ] 新增 `docs/dev-log/2026-08-22.md` 记录 curl 脚本
- [ ] 覆盖 10 条验收标准（proposal.md 列出）
- [ ] 截图记录前端 4 个页面
- **验证**：手动执行 curl 全部返回 `{"code":0}`
