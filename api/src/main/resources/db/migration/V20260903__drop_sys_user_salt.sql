-- ====================================================================
-- V20260903__drop_sys_user_salt.sql
-- 删除 sys_user.salt 冗余列（add-user-auth change）
--
-- 背景：密码使用 BCryptPasswordEncoder 哈希，哈希串本身已内嵌随机盐
-- （$2a$10$<salt><hash>），业务层额外存储的 salt 字段从未参与加密或校验，
-- 属于无效代码。故删除该列并同步移除 Java 实体字段与 Mapper 映射。
-- ====================================================================

ALTER TABLE `sys_user` DROP COLUMN `salt`;