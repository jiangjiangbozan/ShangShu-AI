-- 工作流编辑器表（Dify 风格 DSL）
-- 与 agent/workflow_definition 分离，面向前端画布编辑器
CREATE TABLE IF NOT EXISTS `wf_workflow` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `workflow_id`   VARCHAR(64)  NOT NULL COMMENT '业务 ID（UUID）',
    `name`          VARCHAR(128) NOT NULL DEFAULT '未命名工作流' COMMENT '工作流名称',
    `description`   VARCHAR(512) NULL     DEFAULT '' COMMENT '描述',
    `graph`         LONGTEXT     NOT NULL COMMENT 'DSL JSON（nodes + edges + viewport）',
    `version`       INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    `published`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布',
    `owner_id`      BIGINT       NULL     COMMENT '创建者用户 ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_workflow_id` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义';

-- 工作流运行记录表
CREATE TABLE IF NOT EXISTS `wf_workflow_run` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `run_id`        VARCHAR(64)  NOT NULL COMMENT '运行 ID（UUID）',
    `workflow_id`   VARCHAR(64)  NOT NULL COMMENT '工作流业务 ID',
    `version`       INT          NOT NULL COMMENT '运行版本号',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'running' COMMENT 'running/succeeded/failed/stopped',
    `inputs`        JSON         NULL     COMMENT '运行输入',
    `outputs`       JSON         NULL     COMMENT '运行输出',
    `total_tokens`  BIGINT       NOT NULL DEFAULT 0 COMMENT '消耗 Token 总数',
    `elapsed_ms`    BIGINT       NOT NULL DEFAULT 0 COMMENT '运行耗时(ms)',
    `triggered_by`  VARCHAR(32)  NOT NULL DEFAULT 'manual' COMMENT 'manual/api/schedule',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finished_at`   DATETIME     NULL,
    `error_message` TEXT         NULL     COMMENT '失败原因',
    UNIQUE KEY `uk_run_id` (`run_id`),
    KEY `idx_workflow_id` (`workflow_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流运行记录';

-- 节点运行记录表
CREATE TABLE IF NOT EXISTS `wf_node_run` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `run_id`        VARCHAR(64)  NOT NULL COMMENT '运行 ID',
    `node_id`       VARCHAR(64)  NOT NULL COMMENT '节点 ID（DSL 中的 id）',
    `node_type`     VARCHAR(32)  NOT NULL COMMENT '节点类型(start/end/llm/if-else...)',
    `title`         VARCHAR(128) NULL     COMMENT '节点标题',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'running' COMMENT 'running/succeeded/failed/skipped',
    `inputs`        JSON         NULL     COMMENT '节点输入',
    `outputs`       JSON         NULL     COMMENT '节点输出',
    `elapsed_ms`    BIGINT       NOT NULL DEFAULT 0,
    `tokens`        BIGINT       NOT NULL DEFAULT 0 COMMENT '本节点消耗 Token',
    `error_message` TEXT         NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finished_at`   DATETIME     NULL,
    KEY `idx_run_id` (`run_id`),
    KEY `idx_node_run` (`run_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点运行记录';
