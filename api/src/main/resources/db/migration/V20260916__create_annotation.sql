-- 标注问答管理表（借鉴 Dify Annotation Reply 功能）
CREATE TABLE IF NOT EXISTS `chat_annotation` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `message_id`  BIGINT       NULL     COMMENT '关联消息 ID',
  `question`    TEXT         NOT NULL COMMENT '问题/提问内容',
  `answer`      TEXT         NOT NULL COMMENT '回答内容',
  `hit_count`   INT          NOT NULL DEFAULT 0 COMMENT '命中次数',
  `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
  `creator`     VARCHAR(64)  NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`     VARCHAR(64)  NULL,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_enabled` (`enabled`),
  FULLTEXT KEY `ft_question_answer` (`question`, `answer`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标注问答管理';
