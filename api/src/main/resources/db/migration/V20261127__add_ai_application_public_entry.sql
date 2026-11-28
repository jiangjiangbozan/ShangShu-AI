-- ============================================================
-- AI 应用公开入口配置
-- 背景：扫码进入小程序需要稳定、非凭据的应用标识；api_key 只在服务端内部用于旧 OpenAI 兼容入口，
--   不能放进二维码或前端配置。
-- 影响：只新增可空字段和索引；存量应用需重新发布时补齐 public_code。
-- ============================================================

ALTER TABLE ai_application
    ADD COLUMN public_code VARCHAR(32) NULL COMMENT '扫码入口使用的非敏感公开标识' AFTER api_key_hash,
    ADD COLUMN access_policy VARCHAR(20) NOT NULL DEFAULT 'AUTHENTICATED' COMMENT 'PUBLIC/AUTHENTICATED/ROLE_BASED' AFTER public_code,
    ADD COLUMN required_role VARCHAR(32) NULL COMMENT 'ROLE_BASED 策略要求的角色编码' AFTER access_policy,
    ADD UNIQUE KEY uk_ai_application_public_code (public_code),
    ADD KEY idx_ai_application_public_visibility (enabled, access_policy, required_role);
