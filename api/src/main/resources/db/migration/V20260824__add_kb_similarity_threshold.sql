-- ====================================================================
-- add-rag-optimization: 知识库检索阈值可配置化
-- 依据 dev-log 2026-08-20: QuestionAnswerAdvisor 需要按 KB 维度配置相似度阈值，
-- 默认 0.5 在中文短查询场景下偏低导致无关结果混入。
-- 同时为 chat_message 增加 mq_msg_id 字段（幂等键），用于 RabbitMQ 消费端去重。
-- ====================================================================

ALTER TABLE `knowledge_base`
    ADD COLUMN `similarity_threshold` DOUBLE NOT NULL DEFAULT 0.5
        COMMENT '相似度阈值，QuestionAnswerAdvisor 检索过滤' AFTER `chunk_overlap`;

-- ====================================================================
-- chat_message 幂等键：消费 RabbitMQ 消息时根据 mq_msg_id UNIQUE 约束去重，
-- 防止"retry 3 次后重新投递到死信队列前再次重投"导致重复插入。
-- 详见 RabbitMqConfig.chatMessageLogQueue（已配 x-dead-letter-exchange）。
-- ====================================================================
ALTER TABLE `chat_message`
    ADD COLUMN `mq_msg_id` VARCHAR(64) NULL COMMENT 'RabbitMQ 消息 ID（幂等键）' AFTER `model_id`;

-- MySQL 不支持已存在表加 UNIQUE 索引时 NULL 值冲突检测，
-- 但 mq_msg_id 允许 NULL（非 MQ 来源的消息无此值），UNIQUE 索引中多个 NULL 不冲突。
ALTER TABLE `chat_message`
    ADD UNIQUE INDEX `uk_chat_message_mq_msg_id` (`mq_msg_id`);
