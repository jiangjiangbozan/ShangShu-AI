-- 用户长期记忆表（借鉴 Operit 的本地记忆库设计，走服务端持久化路线）
CREATE TABLE IF NOT EXISTS user_memory (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '所属用户 ID',
    category    VARCHAR(32)  NOT NULL DEFAULT 'general' COMMENT '记忆分类：general/preference/fact/person',
    content     TEXT         NOT NULL COMMENT '记忆内容',
    source      VARCHAR(255) NULL COMMENT '记忆来源（如会话 ID）',
    importance  INT          NOT NULL DEFAULT 0 COMMENT '重要度 0-10，越高越重要',
    times_used  INT          NOT NULL DEFAULT 0 COMMENT '被引用次数',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    creator     VARCHAR(64)  NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater     VARCHAR(64)  NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_user_category (user_id, category),
    INDEX idx_importance (user_id, importance DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户长期记忆';
