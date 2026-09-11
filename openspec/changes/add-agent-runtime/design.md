# Design - add-agent-runtime

> Agent Runtime 工程化模块设计文档。本文档描述 5 个子模块的架构、模块依赖、关键决策与权衡。

## 1. 模块依赖图

```mermaid
graph TD
    subgraph M4 Agent Module
        RT[runtime<br/>AgentRuntime 状态机]
        SK[skill<br/>@Skill 注册 + 5 类型路由]
        WF[workflow<br/>DAG + Plan-Execute]
        TR[trace<br/>全链路追踪]
        MM[memory<br/>短期记忆 + 压缩]
        RE[react-engine<br/>ReAct 循环]
    end

    subgraph M2/M3 复用
        CCF[ChatClientFactory]
        RRS[RagRetrievalService]
        CHAT[ChatStreamService]
    end

    subgraph 数据层
        DB[(MySQL<br/>5 张表)]
        REDIS[(Redis<br/>ctx/runtime/lock)]
        MQ{{RabbitMQ<br/>3 队列}}
    end

    subgraph Controller
        AC[AgentRuntimeController]
        SC[SkillController]
        WC[WorkflowController]
        TC[TraceController]
    end

    RE --> RT
    RE --> SK
    RE --> MM
    RE --> TR
    WF --> SK
    WF --> RT
    WF --> RE
    RT --> DB
    RT --> REDIS
    RT --> MQ
    TR --> MQ
    TR --> DB
    SK --> DB
    SK --> RRS
    MM --> REDIS
    MM --> DB
    RE --> CCF
    RE --> CHAT

    AC --> RT
    SC --> SK
    WC --> WF
    TC --> TR
```

## 2. 包结构

```
com.ai.api.agent
├── controller
│   ├── AgentRuntimeController.java
│   ├── SkillController.java
│   ├── WorkflowController.java
│   └── TraceController.java
├── runtime
│   ├── AgentRuntimeService.java       # 状态机 + start/pause/resume/cancel
│   ├── AgentContextManager.java       # 上下文管理 + snapshot
│   ├── AgentContext.java              # chat history + observations + thought chain
│   ├── AgentTaskConsumer.java         # agent.task.dispatch 消费者
│   ├── HeartbeatScanner.java          # @Scheduled 扫描超时任务
│   └── AgentStateNotifier.java        # agent.state.notify 生产者
├── skill
│   ├── Skill.java                     # @Skill 注解
│   ├── SkillType.java                 # enum: API/LOCAL/KB/SUBAGENT/MCP
│   ├── SkillRegistry.java             # 启动扫描 + invoke 路由
│   ├── SkillInvoker.java              # 按类型分发
│   ├── builtin
│   │   ├── CalculatorSkill.java
│   │   ├── WebSearchSkill.java
│   │   ├── CodeExecutorSkill.java     # Docker 沙箱
│   │   └── KbRetrievalSkill.java
│   └── SkillInitializer.java         # ApplicationRunner 启动注册
├── workflow
│   ├── WorkflowService.java
│   ├── DagEngine.java                 # 拓扑排序 + 并行执行
│   ├── DagNode.java
│   ├── DagEdge.java
│   └── PlanAndExecutePlanner.java
├── trace
│   ├── TraceService.java
│   ├── TraceCollector.java            # agent.trace.collect 消费者
│   └── TraceReplayService.java
├── memory
│   ├── MemoryStore.java              # 接口
│   ├── ShortTermMemoryStore.java     # 滑动窗口 + 压缩
│   └── MemoryCompressor.java         # LLM 摘要压缩
├── react
│   ├── ReactEngine.java              # 主循环
│   ├── ReactPromptBuilder.java       # prompt 模板
│   └── ReactOutputParser.java        # LLM 输出 JSON 解析
├── config
│   ├── AgentProperties.java          # @ConfigurationProperties("agent")
│   └── AgentMqConfig.java            # 队列/交换机/DLX 定义
├── entity
│   ├── AgentRuntime.java
│   ├── AgentTask.java
│   ├── AgentTrace.java
│   ├── SkillDefinition.java
│   └── WorkflowDefinition.java
└── mapper
    ├── AgentRuntimeMapper.java
    ├── AgentTaskMapper.java
    ├── AgentTraceMapper.java
    ├── SkillDefinitionMapper.java
    └── WorkflowDefinitionMapper.java
```

## 3. 数据库表结构

迁移脚本：`api/src/main/resources/db/migration/V20260901__add_agent_engineering_tables.sql`

```sql
-- 1. agent_runtime：运行时状态
CREATE TABLE agent_runtime (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL COMMENT '关联会话 ID',
    agent_config_id BIGINT COMMENT 'Agent 配置 ID',
    parent_runtime_id BIGINT COMMENT '父 runtime（子任务串联）',
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        COMMENT 'pending|running|paused|done|failed|cancelled',
    context_snapshot JSON COMMENT 'AgentContext 序列化快照',
    next_step_index INT NOT NULL DEFAULT 0 COMMENT '续跑起始 step',
    last_heartbeat DATETIME NOT NULL COMMENT '心跳时间',
    ttl_seconds INT NOT NULL DEFAULT 300 COMMENT '心跳超时阈值',
    error_message VARCHAR(500) COMMENT '失败原因',
    final_answer TEXT COMMENT '任务最终输出',
    model_id VARCHAR(64) COMMENT 'LLM modelId',
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_status (session_id, status),
    INDEX idx_heartbeat (last_heartbeat, status),
    INDEX idx_parent (parent_runtime_id)
) COMMENT='Agent Runtime 运行时状态';

-- 2. agent_task：任务实例（与 runtime 1:N，多步规划拆分）
CREATE TABLE agent_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    runtime_id BIGINT NOT NULL,
    step_index INT NOT NULL,
    description VARCHAR(500) NOT NULL COMMENT '任务描述',
    skill_name VARCHAR(64),
    input JSON,
    output JSON,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    parent_step_id BIGINT COMMENT '父任务 step（子任务嵌套）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_runtime_step (runtime_id, step_index)
) COMMENT='Agent 任务步骤实例';

-- 3. agent_trace：全链路追踪
CREATE TABLE agent_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'UUID',
    runtime_id BIGINT NOT NULL,
    step_index INT NOT NULL,
    step_type VARCHAR(20) NOT NULL COMMENT 'thought|action|observation|resume|error',
    skill_id VARCHAR(64) COMMENT 'action 类型必填',
    input JSON,
    output JSON,
    token_in INT NOT NULL DEFAULT 0,
    token_out INT NOT NULL DEFAULT 0,
    latency_ms INT NOT NULL DEFAULT 0,
    parent_step_id BIGINT,
    error VARCHAR(500),
    dlq_message_id VARCHAR(128) COMMENT '死信消息 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_runtime_step (runtime_id, step_index),
    INDEX idx_runtime_type (runtime_id, step_type),
    INDEX idx_parent (parent_step_id)
) COMMENT='Agent 全链路追踪';

-- 4. skill_definition：技能元数据
CREATE TABLE skill_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    version VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    type VARCHAR(20) NOT NULL COMMENT 'api|local|kb|subagent|mcp',
    description VARCHAR(500),
    endpoint VARCHAR(500) COMMENT 'api/mcp 类型的调用地址',
    bean_name VARCHAR(128) COMMENT 'local 类型的 Spring Bean 名',
    method_name VARCHAR(64) COMMENT 'local 类型的调用方法',
    input_schema JSON COMMENT '输入参数 JSON Schema',
    output_schema JSON COMMENT '输出参数 JSON Schema',
    permission_tag VARCHAR(64) DEFAULT '',
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name_version (name, version),
    INDEX idx_type (type, enabled)
) COMMENT='Skill 技能元数据';

-- 5. workflow_definition：工作流定义
CREATE TABLE workflow_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id VARCHAR(64) NOT NULL UNIQUE COMMENT '业务 ID',
    name VARCHAR(128) NOT NULL,
    version VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    dag JSON NOT NULL COMMENT 'DAG JSON',
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT 'manual|api|schedule',
    owner VARCHAR(64),
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workflow_version (workflow_id, version)
) COMMENT='工作流定义';
```

## 4. Redis Key 设计

| Key 模式 | 用途 | TTL |
|---------|------|-----|
| `agent:runtime:{runtimeId}` | 运行时状态热缓存（高频读，避免每次查 DB） | 30 min |
| `agent:ctx:{runtimeId}` | AgentContext 压缩快照 | 24 h |
| `agent:lock:{runtimeId}` | 长程任务分布式锁（防止双跑） | 与任务同生命周期 |
| `agent:queue:task` | 异步任务调度队列（备用 List 模式） | - |

## 5. RabbitMQ 队列

```yaml
# application-dev.yml
agent:
  mq:
    task-dispatch-queue: agent.task.dispatch
    task-dispatch-exchange: agent.task
    task-dispatch-routing-key: dispatch
    task-dlq: agent.task.dlq
    state-notify-queue: agent.state.notify
    state-notify-exchange: agent.state
    trace-collect-queue: agent.trace.collect
    trace-collect-exchange: agent.trace
    trace-collect-routing-key: collect
    trace-dlq: agent.trace.dlq
```

队列声明见 `AgentMqConfig`，所有队列 durable=true，配备 DLX。

## 6. 关键设计决策

### D1: 状态机实现方式 — 枚举 + 显式转移表

不使用 Spring StateMachine（引入重量级依赖），用 `AgentStatus` 枚举 + `Map<AgentStatus, Set<AgentStatus>>` 转移表，非法转移在 `AgentRuntimeService.transition()` 抛 `BusinessException`。

**权衡**：放弃 Spring StateMachine 的事件驱动 / 嵌套状态 / 持久化能力，换取实现简单、依赖最小。M4 状态机扁平，无需嵌套。

### D2: ReactEngine 同步执行 vs 异步消费

**选异步**：`POST /runtimes/{id}/start` 立即返回，任务派发到 `agent.task.dispatch` 队列，消费者同步执行 ReactEngine（单 runtime 串行，多 runtime 并行）。

**权衡**：SSE 实时推送 trace 由 `agent.state.notify` 异步通知实现，前端订阅 runtime 状态变更。优势是断点续跑友好（重启后消费者重新拉起），劣势是 SSE 链路变长，调试复杂度上升。

### D3: Skill 注解扫描时机 — 启动时全量

不引入运行期热加载，`SkillInitializer implements ApplicationRunner`，在 `run()` 中扫描所有 `@Skill` 注解方法，幂等 upsert 到 `skill_definition` 表。

**权衡**：放弃热加载（V2 再做），换取实现简单、版本一致性保证。新增 / 修改 Skill 需要重启应用。

### D4: code_executor 沙箱方案 — Docker API

不引入 GraalVM / JNI 隔离，直接通过 `com.github.docker-java:docker-java` 客户端调用 Docker API，每次执行：

1. `docker.containers().createCmd("python:3.11-slim")` 创建容器
2. 挂载代码到 `/tmp/code.py`
3. `containers().startCmd()` 执行 `python /tmp/code.py`
4. `containers().logsCmd()` 收集 stdout/stderr
5. `containers().removeCmd()` 销毁容器

容器以 `--read-only --security-opt no-new-privileges --network=none` 启动，超时 10s。

**权衡**：启动开销大（每次 ~1s），但隔离强；不引入新的虚拟化栈，依赖 Docker 已有基础设施。M5 可考虑池化容器降低开销。

### D5: MemoryStore 抽象层级

`MemoryStore` 接口同时覆盖短期与长期记忆方法，本 change 实现 `ShortTermMemoryStore`，长期方法抛 `UnsupportedOperationException`。

**权衡**：接口设计 V2-friendly，但当前 V1 实现会让接口里有未实现方法（违反 ISP）。考虑到长期记忆定义尚不清晰，先合并接口，V2 再拆。

### D6: Trace 异步写入策略

ReactEngine 每步完成后向 `agent.trace.collect` 发送 MQ 消息（非阻塞），`TraceCollector` 消费者批量入库（每 100 条或 1 秒 flush）。

**权衡**：trace 写入延迟可能到 1 秒，回放接口需容忍 trace 尚未落库的情况（返回 stale 提示）。优势是 ReactEngine 主流程零阻塞。

## 7. 与现有模块集成点

### 7.1 ChatClientFactory

- `ReactEngine` 注入 `ChatClientFactory`，调用 `getChatClient(modelId)` 获取 LLM 客户端
- 复用 M2 的 modelId 路由逻辑（deepseek / qwen / ollama）

### 7.2 RagRetrievalService

- `KbRetrievalSkill` 注入 `RagRetrievalService`
- 调用 `retrieve(query, kbId)`，命中计数复用 M3 反馈闭环

### 7.3 ChatSessionService

- `AgentRuntimeService` 创建 runtime 时关联 `session_id`（复用现有 chat_session 表）
- Agent 任务最终结果作为 assistant 消息追加到会话历史

### 7.4 RabbitMqConfig

- 新增 `AgentMqConfig`，定义 3 个队列 + 2 个 DLX
- 与现有 `RabbitMqConfig` 共用 Jackson 序列化器配置

## 8. 对外暴露接口（Controller 层）

| Method | Path | 功能 |
|--------|------|------|
| POST | `/api/v1/agent/runtimes` | 创建 runtime |
| POST | `/api/v1/agent/runtimes/{id}/start` | 启动 |
| POST | `/api/v1/agent/runtimes/{id}/pause` | 暂停 |
| POST | `/api/v1/agent/runtimes/{id}/resume` | 恢复 |
| POST | `/api/v1/agent/runtimes/{id}/cancel` | 取消 |
| GET | `/api/v1/agent/runtimes` | 列表（分页） |
| GET | `/api/v1/agent/runtimes/{id}` | 详情 |
| GET | `/api/v1/agent/runtimes/{id}/replay` | Trace 回放 |
| GET | `/api/v1/agent/runtimes/{id}/metrics` | 成本汇总 |
| GET | `/api/v1/agent/skills` | Skill 列表 |
| GET | `/api/v1/agent/skills/{name}` | Skill 详情 |
| POST | `/api/v1/agent/workflows` | 创建工作流 |
| POST | `/api/v1/agent/workflows/{id}/execute` | 执行工作流 |
| GET | `/api/v1/agent/workflows` | 工作流列表 |

## 9. 异常码扩展

`ErrorCode.java` 新增：

```java
AGENT_NOT_FOUND(40401, "runtime not found: %s"),
AGENT_INVALID_STATE_TRANSITION(40009, "非法状态转移: %s → %s"),
AGENT_HEARTBEAT_TIMEOUT(50001, "runtime heartbeat timeout"),
SKILL_NOT_FOUND(40402, "skill not registered: %s"),
SKILL_VERSION_NOT_FOUND(40403, "skill version not found: %s#%s"),
SKILL_INVOKE_FAILED(50002, "skill invoke failed: %s"),
SKILL_NOT_IMPLEMENTED(50101, "skill type not supported in M4: %s"),
WORKFLOW_DAG_INVALID(40010, "workflow dag invalid: %s"),
WORKFLOW_NODE_TIMEOUT(50003, "workflow node timeout: %s"),
MEMORY_COMPRESS_FAILED(50004, "memory compress failed"),
REACT_MAX_ITERATIONS(50005, "max iterations reached"),
```

## 10. 前端模块

```
ai-agent-web/src/
├── api/
│   └── agent.ts                    # Agent / Skill / Workflow / Trace API
├── stores/
│   └── agent.ts                    # Pinia store: runtimes / skills / currentRuntime
├── views/agent/
│   ├── AgentListView.vue           # Agent 列表 + 新建
│   ├── AgentDetailView.vue         # Agent 详情 + 配置
│   ├── RuntimeListView.vue         # Runtime 列表 + 状态徽章
│   └── TraceTimelineView.vue       # Trace 时间轴（折叠 + 展开 raw JSON）
├── components/agent/
│   ├── StatusBadge.vue             # 状态徽章组件
│   ├── SkillSelector.vue           # Skill 多选
│   ├── TraceStep.vue               # 单步 trace 卡片
│   └── TraceStepRaw.vue            # raw JSON 折叠面板
└── router/
    └── agent.ts                    # 路由配置
```

## 11. 验证策略

- **单元测试**：每个 Service / Engine / Manager 用 Mockito mock 依赖，覆盖正常 / 异常路径
- **集成测试**：`@SpringBootTest` + Testcontainers（MySQL / Redis / RabbitMQ），验证状态机 + 断点续跑 + trace 写入
- **手测脚本**：`docs/dev-log/2026-08-22.md` 记录 curl / httpie 脚本覆盖核心接口
