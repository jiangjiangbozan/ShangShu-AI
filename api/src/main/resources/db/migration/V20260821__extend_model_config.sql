-- ====================================================================
-- add-chat-module: 扩展 sys_model_config 表
-- 新增 parameters JSON、api_key_encrypted、sort_order 字段
-- 依据 design.md §数据层结构
-- ====================================================================

ALTER TABLE `sys_model_config`
    ADD COLUMN `parameters` JSON NULL COMMENT '模型参数 JSON（temperature/max_tokens/top_p 等）' AFTER `enabled`,
    ADD COLUMN `api_key_encrypted` VARCHAR(256) NULL COMMENT 'Jasypt 加密后的 API Key' AFTER `api_key`,
    ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重' AFTER `enabled`;