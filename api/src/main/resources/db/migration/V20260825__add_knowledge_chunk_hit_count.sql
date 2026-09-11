-- ====================================================================
-- add-p3-rag-feedback-ops: RAG 检索反馈闭环 - knowledge_chunk 新增 hit_count
-- 检索命中 +1，用户点踩 -3；定期扫描低分/低命中分片触发重新解析与向量化
-- ====================================================================

ALTER TABLE `knowledge_chunk`
    ADD COLUMN `hit_count` INT NOT NULL DEFAULT 0 COMMENT '命中计数（检索 +1，点踩 -3）' AFTER `vector_id`;

-- 历史数据回填默认 0（已由 DEFAULT 0 覆盖，索引仅用于扫描低命中分片）
CREATE INDEX `idx_kchunk_hit` ON `knowledge_chunk` (`hit_count`, `deleted`);
