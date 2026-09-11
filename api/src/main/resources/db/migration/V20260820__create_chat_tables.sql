-- ====================================================================
-- add-chat-module: 对话模块建表
-- 创建 chat_session（会话表）和 chat_message（消息表）
-- 依据 design.md §数据层结构
-- ====================================================================

CREATE TABLE `chat_session` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NOT NULL                COMMENT '用户 ID',
    `title`           VARCHAR(128) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `model_id`        VARCHAR(64)  NOT NULL                COMMENT '使用的模型 ID',
    `context_size`    INT          NOT NULL DEFAULT 10     COMMENT '上下文窗口大小（轮次）',
    `message_count`   INT          NOT NULL DEFAULT 0      COMMENT '消息总数',
    `total_tokens`    BIGINT       NOT NULL DEFAULT 0      COMMENT '累计 Token 用量',
    `creator`         VARCHAR(64)  NOT NULL DEFAULT 'system',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`         VARCHAR(64)  NOT NULL DEFAULT 'system',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_chat_session_user` (`user_id`, `deleted`),
    INDEX `idx_chat_session_update` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

CREATE TABLE `chat_message` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`        BIGINT       NOT NULL                COMMENT '会话 ID',
    `role`              VARCHAR(16)  NOT NULL                COMMENT '角色：user/assistant/system',
    `content`           MEDIUMTEXT   NOT NULL                COMMENT '消息内容',
    `prompt_tokens`     INT          NOT NULL DEFAULT 0      COMMENT '提示词 Token 数',
    `completion_tokens` INT          NOT NULL DEFAULT 0      COMMENT '生成 Token 数',
    `total_tokens`      INT          NOT NULL DEFAULT 0      COMMENT '总 Token 数',
    `latency_ms`        INT          NOT NULL DEFAULT 0      COMMENT '响应延迟（毫秒）',
    `model_id`          VARCHAR(64)  NULL                    COMMENT '使用的模型 ID',
    `creator`           VARCHAR(64)  NOT NULL DEFAULT 'system',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`           VARCHAR(64)  NOT NULL DEFAULT 'system',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_chat_msg_session` (`session_id`, `create_time`),
    INDEX `idx_chat_msg_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';