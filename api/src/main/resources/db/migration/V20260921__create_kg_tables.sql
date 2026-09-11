-- ============================================================
-- 知识图谱表（add-3rd-4th-retrieval，第四路召回：知识图谱召回）
-- 1. kg_entity      实体（知识库内按 name 唯一）
-- 2. kg_relation    实体关系（有向边，type 描述关系语义）
-- 3. kg_entity_chunk 实体-分片关联（召回时通过实体定位到具体分片）
-- 抽取时机：文档上传/重处理完成后由 KnowledgeGraphExtractionService 调 LLM 异步抽取
-- ============================================================

CREATE TABLE kg_entity (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT       NOT NULL COMMENT '所属知识库 ID',
    name              VARCHAR(255) NOT NULL COMMENT '实体名（同知识库内唯一）',
    type              VARCHAR(64)  NULL COMMENT '实体类型（产品/部门/流程/名词等）',
    description       VARCHAR(2000) NULL COMMENT '实体描述（一句话语义）',
    source_document_id BIGINT      NULL COMMENT '抽取来源文档 ID',
    creator           VARCHAR(64)  NULL,
    create_time       DATETIME     NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_kg_entity_kb_name (knowledge_base_id, name)
) COMMENT '知识图谱实体';

CREATE TABLE kg_relation (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT       NOT NULL COMMENT '所属知识库 ID',
    source_entity_id  BIGINT       NOT NULL COMMENT '起点实体 ID',
    target_entity_id  BIGINT       NOT NULL COMMENT '终点实体 ID',
    relation_type     VARCHAR(64)  NOT NULL COMMENT '关系类型（属于/包含/导致/适用于等）',
    source_document_id BIGINT      NULL COMMENT '抽取来源文档 ID',
    creator           VARCHAR(64)  NULL,
    create_time       DATETIME     NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    KEY idx_kg_relation_source (source_entity_id),
    KEY idx_kg_relation_target (target_entity_id)
) COMMENT '知识图谱实体关系';

CREATE TABLE kg_entity_chunk (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT       NOT NULL COMMENT '所属知识库 ID',
    entity_id         BIGINT       NOT NULL COMMENT '实体 ID',
    chunk_id          BIGINT       NOT NULL COMMENT '分片 ID（knowledge_chunk.id）',
    creator           VARCHAR(64)  NULL,
    create_time       DATETIME     NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_kg_entity_chunk (entity_id, chunk_id)
) COMMENT '实体-分片关联';
