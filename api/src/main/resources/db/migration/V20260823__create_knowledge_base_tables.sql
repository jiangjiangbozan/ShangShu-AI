-- ====================================================================
-- add-rag-module: 知识库 RAG 模块建表
-- 依据 design.md §知识库结构
-- 知识库（knowledge_base）→ 文档（knowledge_document）→ 分片（knowledge_chunk）
-- 向量数据存放在 Elasticsearch VectorStore，本表只存分片元信息（document_id 与 ES doc id 关联）
-- ====================================================================

CREATE TABLE `knowledge_base` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(128) NOT NULL                COMMENT '知识库名称',
    `description`   VARCHAR(512) NULL                    COMMENT '知识库描述',
    `owner_id`      BIGINT       NOT NULL                COMMENT '所有者用户 ID',
    `embedding_model` VARCHAR(64) NOT NULL DEFAULT 'ollama' COMMENT '向量化使用的模型 ID',
    `chunk_size`    INT          NOT NULL DEFAULT 800    COMMENT '分片目标字符数',
    `chunk_overlap` INT          NOT NULL DEFAULT 200    COMMENT '分片重叠字符数',
    `document_count` INT         NOT NULL DEFAULT 0      COMMENT '文档总数',
    `chunk_count`   INT          NOT NULL DEFAULT 0      COMMENT '分片总数',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT '状态：active/disabled',
    `creator`       VARCHAR(64)  NOT NULL DEFAULT 'system',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`       VARCHAR(64)  NOT NULL DEFAULT 'system',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_kb_owner` (`owner_id`, `deleted`),
    INDEX `idx_kb_status` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

CREATE TABLE `knowledge_document` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `knowledge_base_id` BIGINT   NOT NULL                COMMENT '所属知识库 ID',
    `file_name`     VARCHAR(255) NOT NULL                COMMENT '原始文件名',
    `file_path`     VARCHAR(512) NOT NULL               COMMENT '存储路径',
    `file_type`     VARCHAR(32)  NOT NULL                COMMENT '文件类型：pdf/docx/md/txt/html',
    `file_size`     BIGINT       NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
    `content_hash`  VARCHAR(64)  NULL                    COMMENT '内容 SHA-256，用于去重',
    `chunk_count`   INT          NOT NULL DEFAULT 0      COMMENT '分片数',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending/parsing/vectorizing/completed/failed',
    `error_message` VARCHAR(1024) NULL                   COMMENT '失败原因',
    `creator`       VARCHAR(64)  NOT NULL DEFAULT 'system',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`       VARCHAR(64)  NOT NULL DEFAULT 'system',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_kdoc_kb` (`knowledge_base_id`, `deleted`),
    INDEX `idx_kdoc_status` (`status`),
    INDEX `idx_kdoc_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

CREATE TABLE `knowledge_chunk` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `knowledge_base_id` BIGINT   NOT NULL                COMMENT '所属知识库 ID',
    `document_id`   BIGINT       NOT NULL                COMMENT '所属文档 ID',
    `chunk_index`   INT          NOT NULL                COMMENT '分片序号',
    `content`       MEDIUMTEXT   NOT NULL                COMMENT '分片文本内容',
    `token_count`   INT          NOT NULL DEFAULT 0      COMMENT 'Token 数（估算）',
    `vector_id`     VARCHAR(128) NULL                    COMMENT 'ES VectorStore 中的 doc id',
    `creator`       VARCHAR(64)  NOT NULL DEFAULT 'system',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_kchunk_doc` (`document_id`, `chunk_index`),
    INDEX `idx_kchunk_kb` (`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分片表';
