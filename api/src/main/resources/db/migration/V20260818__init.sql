-- ====================================================================
-- 智能助手 AI 开放平台 - 初始迁移脚本
-- 创建人：bootstrap-project change
-- 规范依据：design.md D6 / 企业Java后端开发规范.md
-- ====================================================================

-- 模型配置表（本 change 仅用于演示 MyBatis 通路，完整模型注册在 add-model-capability）
CREATE TABLE IF NOT EXISTS `sys_model_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider`     VARCHAR(32)  NOT NULL COMMENT '供应商：deepseek/qwen/ollama',
    `model_id`     VARCHAR(64)  NOT NULL COMMENT '模型标识，如 deepseek-chat',
    `display_name` VARCHAR(128) COMMENT '展示名称',
    `api_key`      VARCHAR(255) COMMENT 'API Key（实际从环境变量读取，本字段留空）',
    `base_url`     VARCHAR(255) COMMENT '基础 URL',
    `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1=是 0=否',
    `creator`      VARCHAR(64)  DEFAULT '' COMMENT '创建人',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      VARCHAR(64)  DEFAULT '' COMMENT '更新人',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：1=是 0=否（逻辑删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_model` (`provider`, `model_id`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统-模型配置表';

-- LLM 调用日志表（异步写入，打通可观测性基础）
CREATE TABLE IF NOT EXISTS `sys_llm_log` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trace_id`          VARCHAR(64) NOT NULL COMMENT '链路追踪 ID',
    `model_id`          VARCHAR(64) NOT NULL COMMENT '模型标识',
    `prompt`            TEXT        COMMENT '用户提示词（超长截断）',
    `response`          TEXT        COMMENT '模型响应内容（超长截断）',
    `prompt_tokens`     INT         NOT NULL DEFAULT 0 COMMENT '输入 Token 数',
    `completion_tokens` INT         NOT NULL DEFAULT 0 COMMENT '输出 Token 数',
    `total_tokens`      INT         NOT NULL DEFAULT 0 COMMENT '总 Token 数',
    `latency_ms`        INT         COMMENT '调用耗时（毫秒）',
    `status`            VARCHAR(16) COMMENT 'success/failed',
    `error_msg`         TEXT        COMMENT '错误信息（失败时）',
    `creator`           VARCHAR(64)  DEFAULT '' COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64)  DEFAULT '' COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：1=是 0=否（逻辑删除）',
    PRIMARY KEY (`id`),
    KEY `idx_trace` (`trace_id`),
    KEY `idx_model_created` (`model_id`, `create_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统-LLM 调用日志表';

-- 初始化默认模型配置（dev 环境）
INSERT INTO `sys_model_config` (`provider`, `model_id`, `display_name`, `base_url`, `enabled`)
VALUES
    ('deepseek', 'deepseek-chat',    'DeepSeek Chat',    'https://api.deepseek.com',                          1),
    ('qwen',     'qwen-plus',         '通义千问 Plus',     'https://dashscope.aliyuncs.com/compatible-mode',     1),
    ('ollama',   'qwen2.5:7b',        'Ollama Qwen2.5 7B', 'http://localhost:11434',                            0)
ON DUPLICATE KEY UPDATE `update_time` = CURRENT_TIMESTAMP;
