-- ====================================================================
-- 角色卡系统：角色卡定义表 + 会话表关联
-- ====================================================================

CREATE TABLE IF NOT EXISTS character_card (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '创建者用户 ID，0 表示系统预设',
    name VARCHAR(64) NOT NULL COMMENT '角色名称',
    avatar VARCHAR(512) NULL DEFAULT '' COMMENT '头像 URL 或 emoji',
    description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '角色简述',
    system_prompt TEXT NOT NULL COMMENT '角色设定 / 系统提示词',
    opening_statement TEXT NULL COMMENT '开场白',
    tags VARCHAR(512) NULL DEFAULT '' COMMENT '标签，逗号分隔',
    is_public TINYINT NOT NULL DEFAULT 0 COMMENT '是否公开',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认角色卡',
    use_count INT NOT NULL DEFAULT 0 COMMENT '使用次数',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    deleted TINYINT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_public (is_public, deleted),
    INDEX idx_default (is_default, deleted)
);

-- 会话表增加角色卡关联
ALTER TABLE chat_session ADD COLUMN character_card_id BIGINT NULL DEFAULT NULL COMMENT '关联角色卡 ID' AFTER model_id;
