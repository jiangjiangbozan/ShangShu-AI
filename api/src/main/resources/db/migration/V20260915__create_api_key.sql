-- API 密钥管理表
CREATE TABLE IF NOT EXISTS `sys_api_key` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(100) NOT NULL DEFAULT '默认密钥' COMMENT '密钥名称',
  `key_prefix`  VARCHAR(20)  NOT NULL COMMENT '密钥前缀（sk-ai-xxxx）',
  `key_hash`    VARCHAR(64)  NOT NULL COMMENT 'SHA-256 哈希',
  `creator_id`  BIGINT       NULL     COMMENT '创建者 ID',
  `last_used_at` DATETIME    NULL     COMMENT '最后使用时间',
  `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
  `creator`     VARCHAR(64)  NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`     VARCHAR(64)  NULL,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_hash` (`key_hash`),
  KEY `idx_creator_id` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 密钥管理';
