-- ====================================================================
-- V20260906__add_rbac_tables.sql
-- 新增 RBAC 权限体系（add-rbac-management change）
-- 依据 design.md D1/D2：用户-角色-权限 数据模型 + 内置 SUPER_ADMIN 种子数据
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. 权限/资源表
-- --------------------------------------------------------------------
CREATE TABLE `sys_permission` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父级菜单 ID（0=根）',
    `code`        VARCHAR(64)  NOT NULL                COMMENT '权限编码，如 system:user:create',
    `name`        VARCHAR(64)  NOT NULL                COMMENT '权限名称',
    `type`        TINYINT      NOT NULL DEFAULT 1      COMMENT '权限类型：1=菜单, 2=操作',
    `path`        VARCHAR(255) NULL                    COMMENT '菜单路由路径（type=1 时使用）',
    `icon`        VARCHAR(128) NULL                    COMMENT '菜单图标',
    `sort`        INT          NOT NULL DEFAULT 0      COMMENT '排序权重',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1=启用, 0=禁用',
    `creator`     VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限/资源表';

-- --------------------------------------------------------------------
-- 2. 角色表
-- --------------------------------------------------------------------
CREATE TABLE `sys_role` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(32) NOT NULL                COMMENT '角色编码（如 SUPER_ADMIN）',
    `name`        VARCHAR(64) NOT NULL                COMMENT '角色名称',
    `remark`      VARCHAR(255) NULL                   COMMENT '备注',
    `status`      TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1=启用, 0=禁用',
    `creator`     VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '更新人',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- --------------------------------------------------------------------
-- 3. 用户-角色关联表
-- --------------------------------------------------------------------
CREATE TABLE `sys_user_role` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT NOT NULL                COMMENT '用户 ID（sys_user.id）',
    `role_id`     BIGINT NOT NULL                COMMENT '角色 ID（sys_role.id）',
    `creator`     VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`, `deleted`),
    KEY `idx_user_role_role` (`role_id`),
    KEY `idx_user_role_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- --------------------------------------------------------------------
-- 4. 角色-权限关联表
-- --------------------------------------------------------------------
CREATE TABLE `sys_role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT NOT NULL                COMMENT '角色 ID（sys_role.id）',
    `permission_id` BIGINT NOT NULL                COMMENT '权限 ID（sys_permission.id）',
    `creator`       VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '更新人',
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`, `deleted`),
    KEY `idx_role_perm_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';

-- ====================================================================
-- 5. 种子数据：菜单/操作权限点
-- ====================================================================
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `sort`) VALUES
(0, 'system:user:menu',       '用户管理', 1, '/system/users', 1),
(0, 'system:role:menu',       '角色管理', 1, '/system/roles', 2),
(0, 'system:permission:menu', '权限管理', 1, '/system/permissions', 3),
(1, 'system:user:create',     '创建用户', 2, NULL, 1),
(1, 'system:user:update',     '编辑用户', 2, NULL, 2),
(1, 'system:user:delete',     '删除用户', 2, NULL, 3),
(1, 'system:user:assign',     '分配角色', 2, NULL, 4),
(2, 'system:role:create',     '创建角色', 2, NULL, 1),
(2, 'system:role:update',     '编辑角色', 2, NULL, 2),
(2, 'system:role:delete',     '删除角色', 2, NULL, 3),
(2, 'system:role:assign',     '分配权限', 2, NULL, 4),
(3, 'system:permission:create', '创建权限', 2, NULL, 1),
(3, 'system:permission:update', '编辑权限', 2, NULL, 2),
(3, 'system:permission:delete', '删除权限', 2, NULL, 3);

-- --------------------------------------------------------------------
-- 6. 种子数据：内置超级管理员角色
-- --------------------------------------------------------------------
INSERT INTO `sys_role` (`code`, `name`, `remark`, `status`) VALUES
('SUPER_ADMIN', '超级管理员', '拥有系统全部权限的内置角色，禁止删除/停用/改编码', 1);

-- 超级管理员全量绑定全部权限点（按 code 关联，避免硬编码自增 id）
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT r.id, p.id, 'system', NOW(), 'system', NOW(), 0
FROM `sys_role` r
CROSS JOIN `sys_permission` p
WHERE r.`code` = 'SUPER_ADMIN' AND p.`deleted` = 0;

-- --------------------------------------------------------------------
-- 7. 引导：将首个用户（ID 最小者）设为超级管理员
--    使平台首次启用即有可用管理员；需调整请在用户管理页完成分配
-- --------------------------------------------------------------------
SET @first_admin_id = (SELECT MIN(id) FROM `sys_user` WHERE `deleted` = 0);
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT @first_admin_id, r.id, 'system', NOW(), 'system', NOW(), 0
FROM `sys_role` r
WHERE r.`code` = 'SUPER_ADMIN' AND @first_admin_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `sys_user_role` ur
      WHERE ur.`user_id` = @first_admin_id AND ur.`role_id` = r.id AND ur.`deleted` = 0
  );