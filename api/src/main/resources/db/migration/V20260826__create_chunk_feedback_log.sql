-- ====================================================================
-- add-p3-rag-feedback-ops: RAG 检索反馈闭环 - 反馈/重处理日志表
-- chunk_feedback_log：记录用户点踩反馈，唯一索引保证点踩幂等
-- chunk_reprocess_log：记录低质分片重处理历史（预留，当前由人工触发）
-- ====================================================================

CREATE TABLE IF NOT EXISTS `chunk_feedback_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `chunk_id`      BIGINT       NOT NULL COMMENT '分片 ID',
    `user_id`       BIGINT       NOT NULL COMMENT '反馈用户 ID',
    `feedback_type` VARCHAR(20)  NOT NULL COMMENT '反馈类型：positive/negative',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_chunk_type` (`user_id`, `chunk_id`, `feedback_type`),
    KEY `idx_chunk` (`chunk_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='分片反馈日志';

CREATE TABLE IF NOT EXISTS `chunk_reprocess_log` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `chunk_id`       BIGINT      NOT NULL COMMENT '分片 ID',
    `trigger_reason` VARCHAR(50) NOT NULL COMMENT '触发原因：low_hit/low_score/manual',
    `status`         VARCHAR(20) NOT NULL COMMENT '处理状态：success/failed/skipped',
    `error_msg`      TEXT        NULL COMMENT '失败原因',
    `start_time`     DATETIME    NOT NULL,
    `end_time`       DATETIME    NULL,
    PRIMARY KEY (`id`),
    KEY `idx_chunk` (`chunk_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='分片重处理日志';
