## Purpose

修复会话列表刷新后选中态闪烁问题，并配合 ES 全局检索能力支持按时间、关键词精细化筛选对话历史。

## MODIFIED Requirements

### Requirement: 会话列表选中态稳定

系统 SHALL 在 `fetchSessions` 执行后主动锚定当前会话 ID，避免计算属性短暂返回 null 导致 DOM 闪烁。

#### Scenario: 刷新会话列表选中态保持

- **WHEN** `chatStore.fetchSessions()` 触发（如新建会话、刷新页面）
- **THEN** 数据返回后立即校验 `currentSessionId` 是否在新列表中
- **AND** 若在则保持选中，若不在则回退到列表第一项
- **AND** 计算属性 `currentSession` 始终返回非 null 值（除非列表为空）

#### Scenario: 空列表场景降级

- **WHEN** 用户没有任何会话
- **THEN** `currentSession` 返回 null
- **AND** 前端展示「请创建新会话」空状态提示

### Requirement: 对话历史按时间与关键词筛选

配合 ES 全局检索能力，前端对话页 SHALL 支持按时间范围与关键词精细化筛选会话历史。

#### Scenario: 时间范围筛选

- **WHEN** 用户在对话页选择时间范围（如近 7 天、近 30 天、自定义）
- **THEN** 会话列表仅展示该时间范围内有消息的会话
- **AND** ES 检索 API 支持 `startTime` 与 `endTime` 参数过滤

#### Scenario: 关键词筛选会话

- **WHEN** 用户在搜索框输入关键词
- **THEN** 调用 `/api/v1/chat/search?q=xxx&startTime=...&endTime=...` 综合检索
- **AND** 返回结果按时间倒序，可点击跳转

#### Scenario: 筛选条件清空

- **WHEN** 用户点击「清空筛选」
- **THEN** 重置时间范围与关键词，恢复默认会话列表
- **AND** 列表回到首条会话选中状态
