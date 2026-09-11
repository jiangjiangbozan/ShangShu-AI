## Purpose

定义大模型调用能力的初版契约：通过 Spring AI 统一封装 DeepSeek、通义千问、Ollama 三种模型的调用入口，提供同步与流式接口。本 change 仅打通调用通路，完整的路由策略与 fallback 机制在后续 `add-model-capability` change 中扩展。

## ADDED Requirements

### Requirement: 大模型连通性测试接口
系统 SHALL 提供 `POST /api/v1/model/ping` 接口，接收 `{modelId, prompt}` 入参，调用对应模型返回文本响应；该接口用于验证 Spring AI 配置与模型 API Key 是否正确，是后续所有 LLM 功能的前置验证点。

#### Scenario: 调用 DeepSeek 成功
- **WHEN** 客户端 POST `{"modelId":"deepseek-chat","prompt":"你好"}`
- **THEN** 返回 `{"code":0,"data":{"content":"<DeepSeek 回复>","modelId":"deepseek-chat","latencyMs":<毫秒数>,"totalTokens":<token 数>}}`
- **AND** 响应延迟不超过 10 秒（取决于模型 API）
- **AND** 后端日志记录 traceId、modelId、prompt（截断 100 字）、response（截断 100 字）、tokens

#### Scenario: 调用 Ollama 本地模型成功
- **WHEN** 客户端 POST `{"modelId":"qwen2.5:7b","prompt":"你好"}` 且本地 Ollama 服务可用
- **THEN** 返回 `{"code":0,"data":{"content":"<本地模型回复>",...}}`
- **AND** latencyMs 反映本地推理耗时

#### Scenario: 模型 API Key 无效
- **WHEN** DeepSeek API Key 配置错误或已过期
- **THEN** 返回 `{"code":22001,"message":"模型调用失败：API Key 无效","data":null,"traceId":"<uuid>"}`
- **AND** 后端日志记录完整异常堆栈

#### Scenario: 模型服务不可达
- **WHEN** Ollama 服务未启动或网络不通
- **THEN** 返回 `{"code":22002,"message":"模型服务不可达","data":null,"traceId":"<uuid>"}`
- **AND** 响应延迟不超过配置的超时时间（默认 30 秒）

### Requirement: 模型配置管理
系统 SHALL 通过 `application.yml` 配置三种模型的连接信息（API Key、Base URL、模型 ID、超时），配置项前缀为 `ai.model.<provider>`，禁止将 API Key 硬编码在代码或提交到 Git。

#### Scenario: DeepSeek 配置
- **WHEN** `application.yml` 包含 `ai.model.deep-seek.api-key: ${DEEPSEEK_API_KEY}` 与 `ai.model.deep-seek.base-url: https://api.deepseek.com`
- **THEN** 应用启动后能读取到 DeepSeek 配置
- **AND** 实际 API Key 从环境变量 `DEEPSEEK_API_KEY` 注入

#### Scenario: API Key 缺失
- **WHEN** 环境变量 `DEEPSEEK_API_KEY` 未设置且配置文件使用占位符
- **THEN** 应用启动失败并明确提示"无法解析占位符 DEEPSEEK_API_KEY"
- **AND** 不产生未授权的 API 调用

### Requirement: Spring AI ChatClient 调用
系统 SHALL 使用 Spring AI 2.0 的 `ChatClient` 抽象调用各模型，禁止在业务代码中直接使用具体模型 SDK；本 change 通过 `modelId` 选择对应的 `ChatClient` Bean。

#### Scenario: 同步调用
- **WHEN** 调用 `ModelPingService.ping("deepseek-chat", "你好")`
- **THEN** 内部使用 `ChatClient` 的 `.prompt().user(prompt).call().content()` 同步获取响应
- **AND** 返回结果包含 content 与 Spring AI 返回的 usage 信息

#### Scenario: Prompt 模板与 Token 控制
- **WHEN** 调用模型时 prompt 长度超过模型上下文限制
- **THEN** 返回 `{"code":22003,"message":"Prompt 超过模型上下文限制","data":null}`
- **AND** 日志记录 prompt 的 token 估算值与模型上限

#### Scenario: 调用日志记录
- **WHEN** 任意一次模型调用完成（无论成功失败）
- **THEN** 异步通过 RabbitMQ `ai.llm.log` 队列记录调用日志
- **AND** 日志包含 traceId、modelId、prompt（截断）、response（截断）、tokens、latencyMs、status
- **AND** 失败时记录 errorMessage
