-- 内容审查 Moderation 配置表（A4）
CREATE TABLE IF NOT EXISTS `sys_moderation_config` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(128)  NOT NULL COMMENT '配置名称',
    `provider`    VARCHAR(32)   NOT NULL DEFAULT 'keyword' COMMENT '审查方式: openai/keyword/disabled',
    `api_key`     VARCHAR(256)  DEFAULT NULL COMMENT 'OpenAI API Key（openai 模式）',
    `api_url`     VARCHAR(256)  DEFAULT NULL COMMENT '自定义接口地址（openai 模式可选覆盖）',
    `threshold`   DECIMAL(4,2)  NOT NULL DEFAULT 0.50 COMMENT '敏感分数阈值 0-1（openai 模式）',
    `keywords`    VARCHAR(1024) DEFAULT NULL COMMENT '敏感词，逗号分隔（keyword 模式）',
    `enabled`     TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否生效: 1 生效 / 0 停用',
    `remark`      VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `creator`     VARCHAR(64)   DEFAULT NULL,
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     VARCHAR(64)   DEFAULT NULL,
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容审查配置';
