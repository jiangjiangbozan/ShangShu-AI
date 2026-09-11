-- ============================================================
-- Agent 执行器策略化 + 暂停恢复机制 数据底座
-- 来源 OpenSpec change: add-agent-runner-pipeline
-- 1) agent_runtime 增加 strategy 字段（COT | FC）
-- 2) 新建 agent_pause_state 表（轻量级暂停点，替代全量快照）
-- 3) 修复 agent_trace 唯一键：runtime_id + step_index + step_type
-- ============================================================

-- 1. agent_runtime：执行策略字段
ALTER TABLE agent_runtime
    ADD COLUMN strategy VARCHAR(20) NOT NULL DEFAULT 'COT'
        COMMENT '执行策略：COT（ReAct 文本解析）| FC（原生 Function Calling）' AFTER agent_config_id;

-- 2. agent_pause_state：Agent 暂停状态（普通暂停 / 人工介入 ask-human）
CREATE TABLE agent_pause_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    runtime_id BIGINT NOT NULL COMMENT '关联 runtime ID',
    next_step_index INT NOT NULL COMMENT '恢复时从该 step 继续执行',
    pause_type VARCHAR(20) NOT NULL
        COMMENT '暂停类型：NORMAL（普通暂停）| ASK_HUMAN（人工介入）',
    question VARCHAR(500) COMMENT 'ask-human 时向用户提出的问题',
    variables JSON COMMENT '待确认变量（人工介入时挂起的中间结果）',
    reply TEXT COMMENT '人工回复内容（resolve 时写入）',
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING'
        COMMENT 'WAITING|RESOLVED|CANCELLED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_runtime_status (runtime_id, status)
) COMMENT='Agent 暂停状态';

-- 3. agent_trace：修复唯一键（同一步可能同时存在 thought/action/observation 多类型记录）
ALTER TABLE agent_trace DROP INDEX uk_runtime_step;
ALTER TABLE agent_trace
    ADD UNIQUE KEY uk_runtime_step_type (runtime_id, step_index, step_type);
