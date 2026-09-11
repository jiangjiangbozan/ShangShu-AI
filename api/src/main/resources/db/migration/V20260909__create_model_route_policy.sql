-- 模型路由 / 降级策略配置表（A5）
CREATE TABLE IF NOT EXISTS `sys_model_route_policy` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `model_id`          VARCHAR(128)  NOT NULL COMMENT '主模型 ID',
    `fallback_model_id` VARCHAR(128)  NOT NULL COMMENT '降级模型 ID',
    `priority`          INT           NOT NULL DEFAULT 10 COMMENT '降级优先级（小者优先，1 最高）',
    `status`            VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT '状态: active/disabled',
    `remark`            VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `creator`           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_model_fallback` (`model_id`, `fallback_model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型路由/降级策略配置';
