-- ====================================================================
-- V20261126__seed_workbench_rbac.sql
-- 种子化客服工作台与数据分析师最小权限模型（mobile-p0）
-- 角色/权限已存在时用 INSERT IGNORE 复用；SUPER_ADMIN 由 RbacPermissionService 动态加载全部权限，不在此重复绑定
-- 时间字段使用表默认 CURRENT_TIMESTAMP，不涉及业务日期边界计算
-- ====================================================================

INSERT IGNORE INTO `sys_role` (`code`, `name`, `remark`, `status`) VALUES
('CUSTOMER_SERVICE', '客服', '客服工作台角色：查看会话并人工接管', 1),
('ANALYST', '数据分析师', '数据分析角色：问数、生成分析报告', 1);

INSERT IGNORE INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `sort`) VALUES
(0, 'cs:session:list', '查看客服会话', 2, NULL, 1),
(0, 'cs:session:takeover', '人工接管会话', 2, NULL, 2),
(0, 'data:analysis:ask', '自然语言问数', 2, NULL, 3),
(0, 'data:analysis:report', '生成分析报告', 2, NULL, 4),
(0, 'data:analysis:schema', '查看数据表清单', 2, NULL, 5);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.`code` IN ('cs:session:list', 'cs:session:takeover')
WHERE r.`code` = 'CUSTOMER_SERVICE' AND r.`deleted` = 0 AND p.`deleted` = 0;

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.`code` IN ('data:analysis:ask', 'data:analysis:report', 'data:analysis:schema')
WHERE r.`code` = 'ANALYST' AND r.`deleted` = 0 AND p.`deleted` = 0;
