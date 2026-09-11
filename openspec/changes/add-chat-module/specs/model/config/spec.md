## Purpose

提供大模型 API 的集中化动态配置管理能力，支持 API Key 安全存储、连接测试、配置热加载，满足生产环境多模型运维需求。

## ADDED Requirements

### Requirement: 模型配置 CRUD

系统 SHALL 提供模型配置的创建、查询、更新和删除管理接口，支持配置多个 Provider（DeepSeek、Qwen、Ollama 等）。

#### Scenario: 创建模型配置
- **WHEN** 管理员向 `POST /api/v1/model/configs` 发送请求，包含 `provider`、`displayName`、`apiKey`、`baseUrl`、`modelId`、`parameters` 等字段
- **THEN** 系统加密存储 `apiKey` 后保存配置，返回 `configId`

#### Scenario: 查询所有模型配置
- **WHEN** 已认证用户向 `GET /api/v1/model/configs` 发送请求
- **THEN** 系统返回所有已启用的模型配置列表（不返回原始 `apiKey`，仅返回脱敏后的版本如 `sk-****xxxx`）

#### Scenario: 更新模型配置
- **WHEN** 管理员向 `PUT /api/v1/model/configs/{configId}` 发送请求，更新 `apiKey` 或 `baseUrl`
- **THEN** 系统更新配置并加密存储，若 `apiKey` 字段为空则保持原有密钥不变

#### Scenario: 删除模型配置
- **WHEN** 管理员向 `DELETE /api/v1/model/configs/{configId}` 发送请求
- **THEN** 系统逻辑删除该配置，若该 Provider 正在被会话使用，已存在的会话不受影响

### Requirement: API 连接测试

系统 SHALL 提供模型 API 连接测试功能，验证配置的 API 是否可用。

#### Scenario: 连接测试成功
- **WHEN** 用户向 `POST /api/v1/model/configs/{configId}/test` 发送请求
- **THEN** 系统使用该配置向 Provider 发送测试请求（如 `ping` 或 `hello`），返回 `{ "success": true, "latencyMs": 350, "modelName": "deepseek-chat" }`

#### Scenario: 连接测试失败
- **WHEN** API Key 无效或 base URL 不可达
- **THEN** 系统返回 `{ "success": false, "error": "Connection refused / 401 Unauthorized" }`，不暴露原始 API Key

#### Scenario: 测试超时处理
- **WHEN** 连接测试超过 10 秒无响应
- **THEN** 系统返回超时错误，不阻塞请求线程

### Requirement: API 密钥安全存储

系统 SHALL 确保所有 API 密钥（API Key）以加密形式存储，禁止在代码、日志、配置文件中明文暴露。

#### Scenario: 密钥加密存储
- **WHEN** 管理员保存模型配置时传入 `apiKey`
- **THEN** 系统使用 AES-256-GCM 加密后存入数据库，在日志中不输出密钥明文

#### Scenario: 密钥脱敏展示
- **WHEN** 前端查询模型配置列表
- **THEN** 后端返回的 `apiKey` 字段仅显示前 4 位和后 4 位，中间用 `****` 替代（如 `sk-a****b12c`）

#### Scenario: 密钥不记录到日志
- **WHEN** 系统处理包含 API Key 的请求
- **THEN** 日志中 `apiKey` 字段被自动替换为 `[REDACTED]`

### Requirement: 配置动态加载

系统 SHALL 支持在不重启应用的情况下动态加载新的模型配置，使新配置的 Provider 立即可用于对话。

#### Scenario: 配置热加载
- **WHEN** 管理员通过 API 新增或更新模型配置
- **THEN** 系统自动刷新 `ChatClientFactory` 中的 Provider 注册表，新配置的模型立即可用于发送消息

#### Scenario: 配置变更不影响已有会话
- **WHEN** 模型配置的 `baseUrl` 或 `apiKey` 被更新
- **THEN** 已有会话的下一次消息发送使用新的配置，不影响已存储的历史消息

#### Scenario: 配置禁用后不可用
- **WHEN** 管理员将模型配置的 `enabled` 设为 `false`
- **THEN** 该模型在模型选择列表中不再出现，已有会话若使用该模型则提示"模型已下线"

### Requirement: 前端配置管理界面

前端 SHALL 提供模型配置管理页面，支持配置的增删改查和连接测试操作。

#### Scenario: 配置列表页面
- **WHEN** 用户进入 `/model-configs` 页面
- **THEN** 显示所有模型配置的表格视图，包含 Provider 名称、显示名称、模型 ID、状态（启用/禁用）、最后更新时间，每行提供"测试连接"和"编辑"按钮

#### Scenario: 配置编辑表单
- **WHEN** 用户点击"编辑"或"新增"按钮
- **THEN** 弹出表单对话框，包含 `provider`、`displayName`、`apiKey`（密码框）、`baseUrl`、`modelId`、`parameters`（JSON 编辑器）、`enabled` 开关

#### Scenario: 连接测试结果展示
- **WHEN** 用户点击"测试连接"按钮
- **THEN** 显示加载动画，测试完成后弹出结果提示（成功绿色 / 失败红色），包含延迟时间和模型名称