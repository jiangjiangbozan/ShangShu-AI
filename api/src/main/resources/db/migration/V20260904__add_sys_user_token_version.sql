-- ====================================================================
-- V20260904__add_sys_user_token_version.sql
-- sys_user 增加令牌版本号列（add-user-auth change）
--
-- 背景：JWT access token 黑名单基于 jti + 剩余有效期 TTL，改密码后
-- 已签发但尚未过期的 access token 无法立即全部作废。引入 token_version
-- 列：签发 JWT 时将版本号写入 payload（ver），每次请求比对 Redis 缓存的
-- 当前版本号；改密码/强制下线时递增该列并失效缓存，旧 token 因版本不
-- 匹配被立即拒绝，实现毫秒级全量作废（无需等待黑名单 TTL 到期）。
-- ====================================================================

ALTER TABLE `sys_user`
    ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT '令牌版本号：递增使既有 JWT 立即失效';