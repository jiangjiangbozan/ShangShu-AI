-- ============================================================
-- M5 智能客服模块数据底座
-- 来源 OpenSpec change: add-customer-service
-- 6 张表：cs_intent / cs_dialog_state / cs_kb_hit_log / cs_label / cs_session / cs_clarify_template
-- ============================================================

-- 1. cs_intent：意图注册表
CREATE TABLE cs_intent (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL UNIQUE COMMENT '意图名称（唯一）',
    description VARCHAR(255) COMMENT '意图描述',
    slots_schema JSON COMMENT '槽位 schema，如 {"region":{"required":true},"product":{"required":true}}',
    fallback_intent VARCHAR(64) NOT NULL DEFAULT 'small_talk' COMMENT '兜底意图',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='客服意图注册表';

-- 2. cs_dialog_state：对话状态（槽位 + 状态机）
CREATE TABLE cs_dialog_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    runtime_id BIGINT NOT NULL COMMENT '关联 AgentRuntime',
    session_id BIGINT NOT NULL COMMENT '关联 chat_session',
    intent VARCHAR(64) NOT NULL COMMENT '当前意图',
    slots JSON COMMENT '已填充槽位，如 {"region":"北京","product":"iPhone"}',
    state VARCHAR(32) NOT NULL DEFAULT 'collecting'
        COMMENT 'collecting|confirming|executing|done',
    clarify_rounds INT NOT NULL DEFAULT 0 COMMENT '已澄清轮数',
    fallback_triggered TINYINT NOT NULL DEFAULT 0 COMMENT '是否已触发兜底意图',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cs_dialog_session (session_id),
    INDEX idx_cs_dialog_runtime (runtime_id)
) COMMENT='客服对话状态表';

-- 3. cs_kb_hit_log：知识库命中日志
CREATE TABLE cs_kb_hit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    runtime_id BIGINT NOT NULL COMMENT '关联 AgentRuntime',
    hit_query VARCHAR(512) COMMENT '原始查询',
    kb_id BIGINT COMMENT '命中的知识库 ID',
    doc_id VARCHAR(128) COMMENT '命中的文档 ID',
    score DECIMAL(5,4) COMMENT '相似度分数',
    hit TINYINT NOT NULL DEFAULT 0 COMMENT '1=命中 0=未命中',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cs_kb_hit_runtime (runtime_id),
    INDEX idx_cs_kb_hit_created (create_time)
) COMMENT='KB 命中日志表';

-- 4. cs_label：人工标注回流
CREATE TABLE cs_label (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL COMMENT '关联 cs_session',
    label_type VARCHAR(16) NOT NULL COMMENT 'positive|negative|neutral',
    label_content TEXT COMMENT '标注内容',
    labeler_id BIGINT NOT NULL COMMENT '标注人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cs_label_session (session_id)
) COMMENT='客服标注回流表';

-- 5. cs_session：客服会话扩展表（复用 chat_session，独立表避免污染）
CREATE TABLE cs_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL UNIQUE COMMENT '关联 chat_session.id（唯一）',
    runtime_id BIGINT COMMENT '关联 AgentRuntime',
    status VARCHAR(16) NOT NULL DEFAULT 'ai'
        COMMENT 'ai|human|done|failed',
    assigned_to BIGINT COMMENT '接管客服用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cs_session_status (status),
    INDEX idx_cs_session_assigned (assigned_to)
) COMMENT='客服会话扩展表';

-- 6. cs_clarify_template：澄清话术模板
CREATE TABLE cs_clarify_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    intent VARCHAR(64) NOT NULL COMMENT '意图名称',
    slot_name VARCHAR(64) NOT NULL COMMENT '槽位名称',
    template VARCHAR(512) NOT NULL COMMENT '话术模板，如 "请告诉我您想查询的地区"',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cs_clarify_intent_slot (intent, slot_name)
) COMMENT='客服澄清话术模板表';

-- 7. 内置意图初始化数据
INSERT INTO cs_intent (name, description, slots_schema, fallback_intent) VALUES
('sales_query', '销量查询', '{"region":{"required":true},"product":{"required":true},"time_range":{"required":false}}', 'small_talk'),
('product_compare', '产品对比', '{"products":{"required":true,"type":"array"},"dimension":{"required":false}}', 'small_talk'),
('reimbursement_standard', '报销标准咨询', '{"category":{"required":true}}', 'small_talk'),
('fault_diagnosis', '故障排查', '{"product":{"required":true},"symptom":{"required":true}}', 'small_talk'),
('small_talk', '闲聊', '{}', 'small_talk');

-- 8. 内置澄清话术模板初始化
INSERT INTO cs_clarify_template (intent, slot_name, template) VALUES
('sales_query', 'region', '请告诉我您想查询的地区'),
('sales_query', 'product', '请告诉我您想查询的产品'),
('sales_query', 'time_range', '请告诉我您想查询的时间范围'),
('product_compare', 'products', '请告诉我您想对比的产品列表'),
('product_compare', 'dimension', '请告诉我您想对比的维度（如价格、性能）'),
('reimbursement_standard', 'category', '请告诉我您想查询的报销类别'),
('fault_diagnosis', 'product', '请告诉我出现故障的产品'),
('fault_diagnosis', 'symptom', '请描述您遇到的故障现象');
