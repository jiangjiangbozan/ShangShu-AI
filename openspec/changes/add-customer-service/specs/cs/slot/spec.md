## Purpose

槽位填充 Skill + 多轮澄清状态机，根据意图的 `slots_schema` + 历史对话，输出缺失槽位并通过 `cs_dialog_state` 持久化状态，支持跨会话恢复。

## ADDED Requirements

### Requirement: 槽位填充 Skill

系统 SHALL 提供 `SlotFillerSkill`（type=local，name=`slot_filler`），输入 `{"query": "<user_query>", "intent": "sales_query", "slots_schema": {...}, "history": [...]}`，输出 `{"filled_slots": {"region": "北京"}, "missing_slots": ["product"], "is_complete": false}`。

#### Scenario: 部分槽位填充

- **WHEN** 用户 query="查一下北京地区 iPhone 销量"
- **AND** 调用 `SlotFillerSkill.eval`
- **THEN** 返回 `filled_slots={"region":"北京","product":"iPhone"}`
- **AND** `missing_slots=[]`，`is_complete=true`

#### Scenario: 缺失槽位触发澄清

- **WHEN** 用户 query="查一下销量"
- **THEN** `filled_slots={}`，`missing_slots=["region", "product"]`
- **AND** 触发澄清话术："请告诉我您想查询的地区和产品"

### Requirement: 澄清状态机

系统 SHALL 维护 4 状态的澄清状态机：`collecting → confirming → executing → done`，通过 `cs_dialog_state.state` 字段持久化。

#### Scenario: 状态转移完整流程

- **GIVEN** 用户 query="查销量"，intent=sales_query
- **WHEN** `slot_filler` 返回 `is_complete=false`
- **THEN** `cs_dialog_state.state=collecting`，触发澄清
- **WHEN** 用户补充 "北京 iPhone"
- **AND** `slot_filler` 返回 `is_complete=true`
- **THEN** `state=confirming`，系统问"确认查询北京地区 iPhone 销量？"
- **WHEN** 用户回答"是"
- **THEN** `state=executing`，调用业务 Skill
- **AND** Skill 返回后 `state=done`

#### Scenario: 跨会话状态恢复

- **GIVEN** 用户会话在 `collecting` 状态被中断（页面关闭）
- **WHEN** 用户 1 小时内重新打开会话
- **THEN** 从 `cs_dialog_state` 加载槽位 + 状态
- **AND** 系统重新触发澄清："请补充产品信息"

### Requirement: 澄清话术模板

系统 SHALL 提供 `cs_clarify_template` 表（字段 `intent` / `slot_name` / `template`），按 slot_type 渲染话术。

#### Scenario: 模板渲染

- **GIVEN** `cs_clarify_template` 配置 `intent=sales_query, slot_name=region, template="请告诉我您想查询的地区"`
- **WHEN** `slot_filler` 缺失 `region` 槽位
- **THEN** 渲染话术 "请告诉我您想查询的地区"
- **AND** 通过 SSE 推送给用户

#### Scenario: 多槽位合并澄清

- **WHEN** 同时缺失 `region` 和 `product`
- **THEN** 合并话术："请告诉我您想查询的地区和产品"
- **AND** 不分多条 SSE 推送
