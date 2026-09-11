## Purpose

提供对话历史跨会话全局检索能力，将 MySQL 写入的消息异步双写到 ES 对话日志索引，对外暴露关键词检索 API 与前端搜索入口。

## ADDED Requirements

### Requirement: 对话历史 ES 双写

`ChatMessageLogConsumer` 成功写入 MySQL 后 SHALL 异步将消息索引到 ES `chat_message_log` 索引，双写失败不影响主流程。

#### Scenario: 双写成功

- **WHEN** 消息成功写入 MySQL
- **THEN** 异步将 `{messageId, sessionId, userId, role, content, modelId, createTime}` 索引到 ES `chat_message_log`
- **AND** 索引字段包含 `content` 走 IK 分词，支持关键词检索

#### Scenario: 双写失败不阻塞主流程

- **WHEN** ES 不可达或索引失败
- **THEN** 记录 ERROR 日志并写入 `chat_message_log_dead` 死信索引（如 ES 持续不可用则降级只记日志）
- **AND** 不抛出异常到 Consumer 主流程，MySQL 消息正常 ack

#### Scenario: ES 索引结构定义

- **WHEN** 应用启动且 `chat_message_log` 索引不存在
- **THEN** 自动创建索引，mapping 包含 `messageId`(keyword)、`sessionId`(keyword)、`userId`(keyword)、`role`(keyword)、`content`(text/ik_smart)、`modelId`(keyword)、`createTime`(date)
- **AND** 设置 1 个分片 1 个副本（适配初期数据量）

### Requirement: 对话历史全局检索 API

系统 SHALL 暴露 `GET /api/v1/chat/search?q=xxx` 接口，支持跨会话关键词检索。

#### Scenario: 关键词检索成功

- **WHEN** 用户调用 `/api/v1/chat/search?q=差旅报销&page=1&size=20`
- **THEN** 返回包含「差旅报销」关键词的消息列表，按 `createTime` 倒序
- **AND** 每条结果包含 `messageId`、`sessionId`、`sessionTitle`、`role`、`content`（高亮关键词）、`createTime`
- **AND** 默认按当前 `userId` 过滤，不跨用户检索

#### Scenario: 检索结果为空

- **WHEN** 用户调用 `/api/v1/chat/search?q=不存在的关键词`
- **THEN** 返回空列表 `[]` 与 200 状态码
- **AND** 分页元数据 `total: 0`

#### Scenario: 无权限检索被拒

- **WHEN** 用户未登录或 Token 失效
- **THEN** 返回 401 状态码
- **AND** 不执行 ES 查询

### Requirement: 前端全局搜索入口

前端对话页 SHALL 提供全局搜索入口，结果点击可跳回原会话上下文。

#### Scenario: 搜索结果跳转

- **WHEN** 用户在搜索框输入关键词并回车
- **THEN** 弹出搜索结果面板，按时间倒序展示匹配消息
- **AND** 点击单条结果跳转到对应 `sessionId`，并滚动到该消息位置

#### Scenario: 搜索防抖

- **WHEN** 用户连续输入关键词（间隔 < 350ms）
- **THEN** 防抖触发，最后一次输入后才调用搜索 API
- **AND** 避免频繁请求打满 ES
