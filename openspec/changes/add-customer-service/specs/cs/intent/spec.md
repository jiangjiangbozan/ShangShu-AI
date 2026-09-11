## Purpose

意图识别 Skill 实现 LLM + 槽位模板双驱动路由，对用户 query 输出 `{intent, confidence, slots_needed}`，并通过阈值控制触发澄清或回退。覆盖 M5 验收指标"意图识别准确率 ≥ 90%"。

## ADDED Requirements

### Requirement: 意图识别 Skill 注册与调用

系统 SHALL 提供 `IntentClassifierSkill`（type=local，name=`intent_classifier`），通过 `@Skill` 注解自动注册到 `skill_definition` 表。输入 `{"query": "<user_query>", "session_id": <id>}`，输出 `{"intent": "sales_query", "confidence": 0.92, "slots_needed": ["region", "product"]}`。

#### Scenario: 高置信度直接路由

- **WHEN** 用户 query="查一下北京地区 iPhone 销量"
- **AND** `IntentClassifierSkill.eval` 返回 `confidence=0.92`
- **THEN** 跳过澄清，直接进入槽位填充阶段
- **AND** `cs_dialog_state` 写入 `intent=sales_query, state=collecting`

#### Scenario: 低置信度触发澄清

- **WHEN** 用户 query="帮我看看那个东西"
- **AND** 返回 `confidence=0.4`（< 阈值 0.6）
- **THEN** 触发澄清话术："请告诉我您具体想查询的产品或信息"
- **AND** 不进入槽位填充阶段，状态保持 `collecting`

### Requirement: 意图注册表

系统 SHALL 提供 `cs_intent` 表管理意图元数据，字段包括 `name`（唯一）、`description`、`slots_schema`（JSON）、`fallback_intent`、`enabled`。

#### Scenario: 意图注册

- **GIVEN** 5 类内置意图：`sales_query` / `product_compare` / `reimbursement_standard` / `fault_diagnosis` / `small_talk`
- **WHEN** 应用启动
- **THEN** `cs_intent` 表写入 5 条记录，`enabled=1`
- **AND** `slots_schema` 示例：`sales_query → {"region":{"required":true},"product":{"required":true},"time_range":{"required":false}}`

#### Scenario: fallback_intent 兜底

- **WHEN** 意图识别置信度 < 0.6 且无澄清轮次剩余
- **THEN** 路由到当前意图的 `fallback_intent`（默认 `small_talk`）
- **AND** `cs_dialog_state.fallback_triggered=1`

### Requirement: 意图识别准确率指标

系统 SHALL 通过 `cs_label` 表回流人工标注数据，按周统计意图识别准确率。

#### Scenario: 准确率统计

- **WHEN** 调用 `GET /api/v1/cs/stats/intent-accuracy?days=7`
- **THEN** 返回 `{"total": 1000, "correct": 920, "accuracy": 0.92, "by_intent": {...}}`
- **AND** `by_intent` 按意图维度拆分准确率
