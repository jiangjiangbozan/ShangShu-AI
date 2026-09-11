-- Prompt 模板管理表
CREATE TABLE IF NOT EXISTS `prompt_template` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(128)  NOT NULL COMMENT '模板名称',
    `category`    VARCHAR(64)   NOT NULL DEFAULT 'chat' COMMENT '分类: chat/reasoning/planning/tool_calling/reflection',
    `content`     TEXT          NOT NULL COMMENT '模板内容，支持 {{variable}} 占位符',
    `variables`   JSON          DEFAULT NULL COMMENT '变量列表，如 ["query","context"]',
    `model_id`    VARCHAR(128)  DEFAULT NULL COMMENT '关联模型 ID（可选）',
    `is_active`   TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    `version`     INT           NOT NULL DEFAULT 1 COMMENT '版本号',
    `description` VARCHAR(512)  DEFAULT NULL COMMENT '模板描述',
    `creator`     VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_name_version` (`name`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板管理';
