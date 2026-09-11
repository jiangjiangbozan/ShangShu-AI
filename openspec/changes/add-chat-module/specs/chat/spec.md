## Purpose

提供完整的对话聊天能力，支持会话管理、消息发送、SSE 流式响应、上下文窗口管理，为后续 RAG、Agent 等高级模块建立对话交互基座。

## ADDED Requirements

### Requirement: 会话管理

系统 SHALL 提供会话的创建、查询、更新和删除功能。每个会话归属于一个已认证用户，包含会话标题和使用的模型配置。

#### Scenario: 创建新会话
- **WHEN** 已认证用户向 `POST /api/v1/chat/sessions` 发送请求，包含 `modelId` 和可选的 `title`
- **THEN** 系统创建一条会话记录，返回 `sessionId` 和默认标题（若未提供则自动生成"新对话"）

#### Scenario: 查询用户会话列表
- **WHEN** 已认证用户向 `GET /api/v1/chat/sessions` 发送请求
- **THEN** 系统返回该用户的所有会话，按 `updateTime` 倒序排列，支持分页

#### Scenario: 删除会话
- **WHEN** 已认证用户向 `DELETE /api/v1/chat/sessions/{sessionId}` 发送请求
- **THEN** 系统逻辑删除该会话及其关联的所有消息，返回成功

#### Scenario: 非所有者无法访问会话
- **WHEN** 已认证用户尝试访问不属于自己的会话
- **THEN** 系统返回 403 权限不足

### Requirement: 消息发送与 SSE 流式响应

系统 SHALL 支持用户发送消息并接收 AI 的流式响应。流式响应使用 SSE（Server-Sent Events）协议，通过 `Flux<ServerSentEvent>` 实现。

#### Scenario: 发送消息并接收流式响应
- **WHEN** 已认证用户向 `POST /api/v1/chat/sessions/{sessionId}/messages/stream` 发送请求，包含 `content` 字段
- **THEN** 系统以 SSE 流式返回 AI 响应，包含 `event: message` 和 `event: done` 两种事件类型，每条 `message` 事件携带 `data` 字段为增量文本片段

#### Scenario: 空消息拒绝
- **WHEN** 用户发送的消息内容为空或仅空白字符
- **THEN** 系统返回 400 参数校验失败

#### Scenario: 会话不存在时发送消息
- **WHEN** 用户向不存在的 `sessionId` 发送消息
- **THEN** 系统返回 404 会话不存在

#### Scenario: 流式响应完成事件
- **WHEN** AI 完成全部响应生成
- **THEN** 系统 SSE 发送 `event: done`，携带 `data` 为 JSON 对象，包含 `messageId`、`promptTokens`、`completionTokens`、`totalTokens`、`latencyMs`

#### Scenario: 流式响应中断处理
- **WHEN** 客户端断开 SSE 连接（如用户点击"停止生成"）
- **THEN** 系统释放相关资源，已生成的部分消息内容保存到数据库

### Requirement: 消息历史查询

系统 SHALL 提供会话内消息历史的分页查询能力，按时间正序排列。

#### Scenario: 查询会话消息历史
- **WHEN** 已认证用户向 `GET /api/v1/chat/sessions/{sessionId}/messages` 发送请求，可选 `page` 和 `size` 参数
- **THEN** 系统返回该会话的消息列表，按 `createTime` 正序排列，支持分页

#### Scenario: 消息历史不含其他会话
- **WHEN** 用户查询会话 A 的消息历史
- **THEN** 系统仅返回会话 A 的消息，不包含会话 B 的消息

### Requirement: 上下文窗口管理

系统 SHALL 在发送消息时自动管理对话上下文窗口，将最近 N 轮对话（用户消息 + AI 回复）作为上下文传递给大模型。

#### Scenario: 上下文窗口自动截断
- **WHEN** 会话内消息数量超过配置的上下文窗口大小（默认 10 轮）
- **THEN** 系统仅取最近 10 轮对话作为 Prompt 上下文，超出部分被截断

#### Scenario: 上下文窗口可配置
- **WHEN** 管理员修改 `ai.chat.context-window-size` 配置
- **THEN** 后续消息发送使用新的窗口大小

### Requirement: Token 计数

系统 SHALL 在每次 AI 响应完成后记录 Token 用量（prompt tokens、completion tokens、total tokens），并写入数据库。

#### Scenario: 记录 Token 用量
- **WHEN** AI 流式响应完成
- **THEN** 系统从 Spring AI `ChatResponse` 的 `Usage` 对象中提取 Token 统计，写入 `chat_message` 记录的对应字段

#### Scenario: Token 用量统计接口
- **WHEN** 已认证用户向 `GET /api/v1/chat/tokens/stats` 发送请求，可选 `period` 参数（today/week/month）
- **THEN** 系统返回该用户在指定时段内的 Token 用量汇总（totalPromptTokens、totalCompletionTokens、totalTokens）

### Requirement: 前端对话页面

前端 SHALL 提供完整的对话交互界面，包括侧边栏会话列表、主对话区、SSE 打字机效果和 Markdown 渲染。

#### Scenario: 左侧会话列表
- **WHEN** 用户进入 `/chat` 页面
- **THEN** 左侧显示会话列表，包含"新建对话"按钮，每个会话项显示标题和最后更新时间，当前选中会话高亮

#### Scenario: 主对话区 SSE 打字机效果
- **WHEN** 用户发送消息后 AI 开始流式响应
- **THEN** 主对话区以打字机效果逐字显示 AI 回复，同时显示"正在输入…"动画指示器

#### Scenario: Markdown 渲染
- **WHEN** AI 回复内容包含 Markdown 格式（代码块、列表、表格、加粗等）
- **THEN** 前端使用 `marked` 库将内容渲染为格式化的 HTML，代码块支持语法高亮

#### Scenario: 停止生成
- **WHEN** 用户在 AI 流式响应过程中点击"停止生成"按钮
- **THEN** 前端断开 SSE 连接，已生成的部分内容保留在对话区