-- 操作审计日志表（A6）
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT        DEFAULT NULL COMMENT '操作人 ID',
    `username`    VARCHAR(64)   DEFAULT NULL COMMENT '操作人用户名',
    `module`      VARCHAR(64)   NOT NULL COMMENT '模块（如 model-config / knowledge / agent）',
    `action`      VARCHAR(64)   NOT NULL COMMENT '动作（如 create / update / delete / query）',
    `method`      VARCHAR(16)   DEFAULT NULL COMMENT 'HTTP 方法',
    `path`        VARCHAR(255)  DEFAULT NULL COMMENT '请求路径',
    `ip`          VARCHAR(64)   DEFAULT NULL COMMENT '来源 IP',
    `params`      TEXT          COMMENT '请求参数（截断）',
    `status`      VARCHAR(16)   NOT NULL DEFAULT 'success' COMMENT '结果: success/fail',
    `error_msg`   VARCHAR(512)  DEFAULT NULL COMMENT '失败原因',
    `duration_ms` BIGINT        DEFAULT NULL COMMENT '耗时(ms)',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `deleted`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_module_status` (`module`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';
