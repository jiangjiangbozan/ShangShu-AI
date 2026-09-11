-- ============================================================
-- M4 Agent Runtime 工程化模块数据底座
-- 来源 OpenSpec change: add-agent-runtime
-- 5 张表：agent_runtime / agent_task / agent_trace / skill_definition / workflow_definition
-- ============================================================

-- 1. agent_runtime：Agent 运行时状态
CREATE TABLE agent_runtime (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL COMMENT '关联会话 ID（chat_session.id）',
    agent_config_id BIGINT COMMENT 'Agent 配置 ID（M6 完善）',
    parent_runtime_id BIGINT COMMENT '父 runtime（子任务串联）',
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        COMMENT 'pending|running|paused|done|failed|cancelled',
    context_snapshot JSON COMMENT 'AgentContext 序列化快照（chat history + observations + thought chain）',
    next_step_index INT NOT NULL DEFAULT 0 COMMENT '续跑起始 step',
    last_heartbeat DATETIME NOT NULL COMMENT '心跳时间，扫描 running>ttl 转失败的依据',
    ttl_seconds INT NOT NULL DEFAULT 300 COMMENT '心跳超时阈值（秒）',
    error_message VARCHAR(500) COMMENT '失败原因',
    final_answer TEXT COMMENT '任务最终输出（done 时写入）',
    model_id VARCHAR(64) COMMENT 'LLM modelId（路由 ChatClient）',
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_status (session_id, status),
    INDEX idx_heartbeat (last_heartbeat, status),
    INDEX idx_parent (parent_runtime_id)
) COMMENT='Agent Runtime 运行时状态';

-- 2. agent_task：任务步骤实例（与 runtime 1:N，多步规划拆分）
CREATE TABLE agent_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    runtime_id BIGINT NOT NULL,
    step_index INT NOT NULL COMMENT '步骤序号（从 0 递增）',
    description VARCHAR(500) NOT NULL COMMENT '步骤描述（LLM 规划产出）',
    skill_name VARCHAR(64) COMMENT '调用的 Skill 名',
    skill_version VARCHAR(20) COMMENT '调用的 Skill 版本',
    input JSON COMMENT '步骤输入',
    output JSON COMMENT '步骤输出',
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        COMMENT 'pending|running|done|failed|skipped',
    parent_step_id BIGINT COMMENT '父任务 step（子任务嵌套）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_runtime_step (runtime_id, step_index),
    INDEX idx_parent (parent_step_id)
) COMMENT='Agent 任务步骤实例';

-- 3. agent_trace：全链路追踪（Thought/Action/Observation）
CREATE TABLE agent_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'UUID',
    runtime_id BIGINT NOT NULL,
    step_index INT NOT NULL,
    step_type VARCHAR(20) NOT NULL
        COMMENT 'thought|action|observation|resume|error',
    skill_id VARCHAR(64) COMMENT 'action 类型必填',
    input JSON COMMENT 'action 的输入 / thought 的 prompt',
    output JSON COMMENT 'action 的返回 / thought 的 LLM 响应',
    token_in INT NOT NULL DEFAULT 0 COMMENT 'LLM 输入 token（非 LLM 步骤为 0）',
    token_out INT NOT NULL DEFAULT 0 COMMENT 'LLM 输出 token',
    latency_ms INT NOT NULL DEFAULT 0 COMMENT '本步耗时毫秒',
    parent_step_id BIGINT COMMENT '子任务嵌套时关联父步骤',
    error VARCHAR(500) COMMENT '异常信息（null 表示成功）',
    dlq_message_id VARCHAR(128) COMMENT '死信消息 ID（异常归因）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_runtime_step (runtime_id, step_index),
    INDEX idx_runtime_type (runtime_id, step_type),
    INDEX idx_parent (parent_step_id)
) COMMENT='Agent 全链路追踪';

-- 4. skill_definition：Skill 技能元数据
CREATE TABLE skill_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL COMMENT '技能名（kebab-case）',
    version VARCHAR(20) NOT NULL DEFAULT '1.0.0' COMMENT '语义版本号',
    type VARCHAR(20) NOT NULL
        COMMENT 'api|local|kb|subagent|mcp',
    description VARCHAR(500) COMMENT '技能描述（供 LLM 工具选择 prompt）',
    endpoint VARCHAR(500) COMMENT 'api/mcp 类型的调用地址',
    bean_name VARCHAR(128) COMMENT 'local 类型的 Spring Bean 名',
    method_name VARCHAR(64) COMMENT 'local 类型的调用方法名',
    input_schema JSON COMMENT '输入参数 JSON Schema',
    output_schema JSON COMMENT '输出参数 JSON Schema',
    permission_tag VARCHAR(64) NOT NULL DEFAULT '' COMMENT '权限标签（M6 RBAC 校验）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 / 0=禁用',
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
    dag JSON NOT NULL COMMENT 'DAG JSON（nodes + edges）',
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'manual'
        COMMENT 'manual|api|schedule',
    owner VARCHAR(64),
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workflow_version (workflow_id, version)
) COMMENT='工作流定义';
