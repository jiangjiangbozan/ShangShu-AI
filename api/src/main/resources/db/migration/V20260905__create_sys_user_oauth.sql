-- ====================================================================
-- V20260905__create_sys_user_oauth.sql
-- 创建第三方 OAuth 账号绑定表（add-oauth-gitee change）
-- 用于将第三方平台用户（Gitee 等）与本地 sys_user 账号做一对一映射
-- ====================================================================

CREATE TABLE `sys_user_oauth` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL                COMMENT '绑定用户 ID（sys_user.id）',
    `provider`    VARCHAR(32)  NOT NULL                COMMENT '第三方平台标识：gitee',
    `open_id`     VARCHAR(128) NOT NULL                COMMENT '平台侧用户唯一标识（Gitee 用户 id）',
    `union_id`    VARCHAR(128) NULL                    COMMENT '跨应用统一标识（预留，Gitee 无）',
    `nickname`    VARCHAR(64)  NULL                    COMMENT '平台昵称',
    `avatar_url`  VARCHAR(512) NULL                    COMMENT '平台头像地址',
    `raw_profile` TEXT         NULL                    COMMENT '平台返回的原始用户信息快照（JSON，便于排查）',
    `creator`     VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_oauth_provider_openid` (`provider`, `open_id`),
    KEY `idx_user_oauth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方 OAuth 账号绑定表';