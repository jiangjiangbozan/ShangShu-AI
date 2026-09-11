## Purpose

针对用户口语化长问句导致向量召回偏差的问题，调用轻量大模型将原始 query 改写为 2-3 个语义等价的检索问句，多路并行召回后通过 RRF 融合，提升召回准确率，适配内部文档术语密集场景。

## ADDED Requirements

### Requirement: Query 重写服务

系统 SHALL 提供 `QueryRewriteService.rewrite(rawQuery)` 接口，调用轻量大模型生成 2-3 个语义等价的检索问句。

#### Scenario: 正常 query 重写

- **WHEN** 用户输入「我们公司出差报销标准是怎么样的」
- **THEN** 调用轻量大模型（默认 DeepSeek，fallback Ollama Qwen2.5）改写为 2-3 个等价问句：
  - 「公司差旅报销标准」
  - 「员工出差报销政策」
  - 「差旅费用报销流程」
- **AND** 返回 List<String>，至少 2 条，最多 3 条

#### Scenario: query 过短不重写

- **WHEN** 用户输入 query 长度 < 6 字符（如「hi」）
- **THEN** 跳过重写，直接用原 query 做向量检索
- **AND** 避免无意义改写消耗 LLM Token

#### Scenario: LLM 调用失败降级

- **WHEN** 大模型 API 不可达或超时（默认 3s）
- **THEN** 降级使用原 query 做单路检索
- **AND** 记录 WARN 日志，不抛出异常到主流程

### Requirement: LLM Prompt 与 Token 控制

`QueryRewriteService` SHALL 使用固定 Prompt 模板，并控制 LLM 调用 Token 预算。

#### Scenario: Prompt 模板规范化

- **WHEN** 调用重写服务
- **THEN** 使用模板：
  ```
  你是查询改写助手。将用户原始问题改写为 2-3 个语义等价但更精炼的检索问句，便于向量召回。
  要求：
  1. 每条问句 ≤ 20 字
  2. 保留核心实体与意图
  3. 输出 JSON 数组，如 ["问句1","问句2","问句3"]
  原始问题：{query}
  ```
- **AND** `max_tokens` 设为 200，`temperature` 设为 0.3，避免发散

#### Scenario: Token 预算超限保护

- **WHEN** 改写后总 token 数超过 200
- **THEN** 截断到前 3 条问句
- **AND** 记录 INFO 日志，统计截断次数

### Requirement: 多路召回与 RRF 融合

系统 SHALL 对原 query + 改写后 query 并行执行向量召回，通过 RRF（Reciprocal Rank Fusion）融合最终结果。

#### Scenario: 多路并行召回

- **WHEN** 触发 RAG 检索且 query 重写成功
- **THEN** 对原 query + N 条改写 query 并行执行向量检索（topK=5）
- **AND** 并行调度通过 `Flux.merge` 实现，单路超时 2s 不影响其他路

#### Scenario: RRF 融合排序

- **WHEN** 多路召回结果返回
- **THEN** 按 RRF 公式 `score = Σ 1/(k + rank_i)`（k=60 默认）计算每条 chunk 的融合分
- **AND** 去重后按融合分倒序取 topK=5 返回
- **AND** 同一 chunk 在多路中出现时分数累加

#### Scenario: 单路召回失败容错

- **WHEN** 某一路召回超时或异常
- **THEN** 该路贡献为 0，其他路正常参与融合
- **AND** 不影响最终结果返回
