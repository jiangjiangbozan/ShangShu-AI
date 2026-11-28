-- ============================================================
-- 知识库文档双语摘要（cross-language）
-- 背景：kb3/kb4/kb5 语料为中文文档，summary 为中文；跨语言检索与双语
--   展示需要英文摘要。英文摘要由 qwen-mt-uni 离线翻译回填
--   （dev/rag-corpus/backfill_summary_en.py），不占用 embedding 额度。
-- 影响：只新增可空字段；存量 255 篇由脚本回填。
-- ============================================================

ALTER TABLE knowledge_document
    ADD COLUMN summary_en VARCHAR(512) NULL COMMENT '英文摘要（qwen-mt-uni 翻译）' AFTER qa_json;
