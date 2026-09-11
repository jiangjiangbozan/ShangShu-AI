-- ====================================================================
-- 启用 Ollama 本地模型并切换为 qwen3:8b
-- 背景：本地已安装 Ollama 并拉取 qwen3:8b 模型，
--       原初始化脚本中 ollama 配置为 qwen2.5:7b 且 enabled=0，导致对话无响应。
-- ====================================================================

UPDATE `sys_model_config`
SET `model_id`     = 'qwen3:8b',
    `display_name` = 'Ollama Qwen3 8B',
    `base_url`     = 'http://localhost:11434',
    `enabled`      = 1
WHERE `provider` = 'ollama';
