## Purpose

客服工作台：会话列表、人工接管、标注回流，支持运营查看会话状态、接管 ReactEngine 异常循环、回流正负样本到 `cs_label` 用于模型微调。

## ADDED Requirements

### Requirement: 会话列表接口

系统 SHALL 提供 `GET /api/v1/cs/sessions` 接口，分页返回客服会话列表，支持按 `status`（ai/human/done/failed）、`assignedTo`、`createdAt` 范围过滤。

#### Scenario: 按状态过滤

- **WHEN** `GET /api/v1/cs/sessions?status=human&page=1&size=20`
- **THEN** 返回 `{"code":0,"data":{"records":[...],"total":42,"page":1,"size":20}}`
- **AND** `records` 只包含 `status=human` 的会话

### Requirement: 人工接管

系统 SHALL 提供 `POST /api/v1/cs/sessions/{id}/takeover` 接口，调用后 ReactEngine 循环停止，会话状态转 `human`，`assignedTo` 设为当前用户。

#### Scenario: 客服接管 AI 会话

- **GIVEN** 会话状态为 `ai`，ReactEngine 正在运行
- **WHEN** `POST /api/v1/cs/sessions/123/takeover`
- **THEN** runtime 状态转 `paused`，会话状态转 `human`
- **AND** `assigned_to` 字段设为当前客服用户 ID
- **AND** 后续消息走人工通道，不再调用 ReactEngine

### Requirement: 标注回流

系统 SHALL 提供 `POST /api/v1/cs/sessions/{id}/label` 接口，写入 `cs_label` 表，字段包括 `session_id` / `label_type`（positive/negative/neutral）/ `label_content` / `labeler_id`。

#### Scenario: 标注负样本

- **WHEN** `POST /api/v1/cs/sessions/123/label` body=`{"label_type":"negative","label_content":"意图识别错误，应该是 product_compare"}`
- **THEN** `cs_label` 表写入一条记录
- **AND** 关联的 `cs_dialog_state.intent` 字段记录原始意图，用于对比

### Requirement: 工作台前端

前端 SHALL 提供 `CustomerServiceView.vue`：左侧会话列表 + 右侧对话窗口 + 顶部标签栏，支持接管 / 转人工 / 标注三个操作。

#### Scenario: 接管按钮

- **WHEN** 客服点击会话右侧 "接管" 按钮
- **THEN** 调用 `POST /api/v1/cs/sessions/{id}/takeover`
- **AND** 会话状态徽章转 "人工"，输入框切换为人工发送模式

#### Scenario: 标注按钮

- **WHEN** 客服选择"负样本"并填写标注内容
- **THEN** 调用 `POST /api/v1/cs/sessions/{id}/label`
- **AND** 成功后 Toast 提示"标注已提交"，列表中会话标记 "已标注"
