-- ====================================================================
-- V20260819__create_sys_user.sql
-- 创建系统用户表（add-user-auth change）
-- 依据 design.md "sys_user 表 DDL"
-- ====================================================================

CREATE TABLE `sys_user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(32)  NOT NULL                COMMENT '用户名（3-32 字符，字母数字下划线）',
    `email`           VARCHAR(128) NOT NULL                COMMENT '邮箱',
    `password_hash`   VARCHAR(72)  NOT NULL                COMMENT 'BCrypt 哈希（含盐与版本前缀）',
    `salt`            VARCHAR(32)  NOT NULL                COMMENT '业务层附加熵（与 BCrypt 内置盐组合）',
    `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1=正常, 0=禁用',
    `fail_count`      INT          NOT NULL DEFAULT 0      COMMENT '连续登录失败次数',
    `lock_until`      DATETIME     NULL                    COMMENT '锁定截止时间（NULL 表示未锁定）',
    `last_login_at`   DATETIME     NULL                    COMMENT '最近登录时间',
    -- BaseDO 审计字段（与现有规范一致）
    `creator`         VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    -- 唯一索引含 deleted 字段，避免逻辑删除后无法注册同名账号
    UNIQUE KEY `uk_user_username` (`username`, `deleted`),
    UNIQUE KEY `uk_user_email` (`email`, `deleted`),
    -- 登录查询索引
    KEY `idx_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';
