## Purpose

增强文档管理能力，提供文档与问答记录的关联视图、批量操作能力，提升知识库运营效率。

## ADDED Requirements

### Requirement: 文档关联问答记录展示

文档详情页 SHALL 展示该文档被哪些会话命中引用，辅助内容运营与调优。

#### Scenario: 关联问答记录加载

- **WHEN** 用户访问 `/api/v1/documents/{docId}` 详情页
- **THEN** 调用 `DocumentRelationService.getRelatedChats(docId)` 返回引用该文档的会话列表
- **AND** 每条包含 `sessionId`、`sessionTitle`、`hitTime`、`querySnippet`、`chunkContent`
- **AND** 列表按 `hitTime` 倒序，默认 top 20

#### Scenario: 无关联记录

- **WHEN** 文档未被任何会话引用
- **THEN** 返回空列表与提示「暂无关联问答记录」
- **AND** 不影响详情页其他信息展示

#### Scenario: 跳转回原会话

- **WHEN** 用户点击某条关联记录
- **THEN** 跳转到对应 `sessionId` 对话页
- **AND** 滚动到 `querySnippet` 对应消息位置

### Requirement: 批量删除文档

文档列表 SHALL 支持批量勾选后一次性删除，减少运营操作成本。

#### Scenario: 批量删除成功

- **WHEN** 用户在文档列表勾选 N 个文档点击「批量删除」
- **THEN** 调用 `POST /api/v1/documents/batch/delete`，参数 `docIds: [...]`
- **AND** 后端事务删除 MySQL 记录 + ES 索引 + 向量数据
- **AND** 返回成功删除数量与失败详情列表

#### Scenario: 部分删除失败容错

- **WHEN** 批量删除中部分 docId 不存在或 ES 删除失败
- **THEN** 已成功删除的提交，失败项返回详情
- **AND** 响应包含 `successCount` 与 `failures: [{docId, reason}]`

### Requirement: 批量重处理文档

文档列表 SHALL 支持批量重处理（重新解析 + 向量化）操作。

#### Scenario: 批量重处理触发

- **WHEN** 用户勾选 N 个文档点击「批量重处理」
- **THEN** 调用 `POST /api/v1/documents/batch/reprocess`，参数 `docIds: [...]`
- **AND** 异步队列处理，接口立即返回「已提交」状态
- **AND** 通过 `GET /api/v1/documents/batch/reprocess/status/{batchId}` 查询进度

#### Scenario: 重处理进度查询

- **WHEN** 用户查询批处理进度
- **THEN** 返回 `{total, success, failed, processing, pending}` 状态
- **AND** 失败项提供错误原因

#### Scenario: 重复提交拦截

- **WHEN** 同一 docId 已在重处理队列中
- **THEN** 跳过该 docId，避免重复入队
- **AND** 返回提示「该文档正在处理中」
