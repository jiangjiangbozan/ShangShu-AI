## Purpose

知识库匹配回退：在 LLM 调用前先检索 M3 RAG，命中 top1 score > 阈值则直接返回，跳过 LLM 降低成本与幻觉风险，命中记录写 `cs_kb_hit_log` 用于运营分析。

## ADDED Requirements

### Requirement: KB 命中阈值路由

系统 SHALL 在意图识别成功后、业务 Skill 执行前，调用 `RagRetrievalService.search(query, topK=1)`，若 top1 score > `cs.kb-hit-threshold`（默认 0.85）则跳过 LLM 直接返回。

#### Scenario: 命中阈值直接返回

- **GIVEN** `cs.kb-hit-threshold=0.85`
- **WHEN** 用户 query="公司差旅报销标准是什么？"
- **AND** `RagRetrievalService.search` 返回 top1 `score=0.92`
- **THEN** 跳过 LLM 调用，直接返回知识库片段作为答案
- **AND** `cs_kb_hit_log` 写入一条记录

#### Scenario: 未命中走 LLM

- **WHEN** 用户 query="查北京 iPhone 销量"
- **AND** top1 `score=0.3` < 0.85
- **THEN** 继续走 LLM + 业务 Skill 流程
- **AND** `cs_kb_hit_log` 写入 `hit=0` 记录

### Requirement: 命中日志统计

系统 SHALL 提供 `GET /api/v1/cs/stats/kb-hit` 接口，按天 / 周统计命中率，支持按 `intent` 维度过滤。

#### Scenario: 按周统计命中率

- **WHEN** `GET /api/v1/cs/stats/kb-hit?days=7`
- **THEN** 返回 `{"total": 5000, "hit": 1500, "hit_rate": 0.30, "by_intent": {"reimbursement_standard": 0.65, ...}}`
- **AND** `by_intent` 按意图维度拆分命中率

### Requirement: 命中记录回流

`cs_kb_hit_log` 表字段 SHALL 包含 `runtime_id` / `hit_query` / `kb_id` / `doc_id` / `score` / `hit` / `created_at`，用于运营分析低命中场景。

#### Scenario: 命中记录写入

- **WHEN** KB 检索完成
- **THEN** INSERT 一条 `cs_kb_hit_log`
- **AND** `runtime_id` 关联当前 runtime，`hit=1` 表示命中，`hit=0` 表示未命中
