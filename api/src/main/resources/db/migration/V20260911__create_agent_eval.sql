-- Agent 评测记录表（A3）：人工对一次 Agent 运行结果打分/标记
CREATE TABLE IF NOT EXISTS `sys_agent_eval` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `runtime_id`      BIGINT       NOT NULL COMMENT 'Agent 运行 ID',
    `session_id`      BIGINT       DEFAULT NULL COMMENT '关联会话 ID',
    `agent_config_id` BIGINT       DEFAULT NULL COMMENT 'Agent 配置 ID',
    `model_id`        VARCHAR(128) DEFAULT NULL COMMENT '使用的模型',
    `user_id`         BIGINT       DEFAULT NULL COMMENT '评测人',
    `status`          VARCHAR(16)  DEFAULT NULL COMMENT '运行结果: success/failed',
    `input`           TEXT         COMMENT '用户输入',
    `output`          TEXT         COMMENT '最终输出',
    `tokens`          BIGINT       DEFAULT NULL COMMENT '总 token',
    `latency_ms`      BIGINT       DEFAULT NULL COMMENT '总耗时(ms)',
    `score`           INT          DEFAULT NULL COMMENT '人工评分 1-5',
    `verdict`         VARCHAR(16)  DEFAULT 'none' COMMENT '评价: good/bad/none',
    `note`            VARCHAR(500) DEFAULT NULL COMMENT '评价备注',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评测时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_runtime` (`runtime_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 评测记录';
