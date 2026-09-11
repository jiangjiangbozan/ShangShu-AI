## Purpose

将 RabbitMQ 消息序列化方式从手写 `escapeJson` 字符串拼接升级为 Jackson `ObjectMapper` 标准序列化，彻底解决 emoji / 特殊控制字符场景的序列化异常，避免消息消费失败与脏数据。

## ADDED Requirements

### Requirement: Jackson 统一序列化

所有 RabbitMQ 消息生产者 SHALL 使用 Jackson `ObjectMapper` 序列化 payload 为 JSON bytes，并通过 `MessageProperties.CONTENT_TYPE_JSON` 标注 content-type。

#### Scenario: 含 emoji 的消息序列化

- **WHEN** 消息 content 包含 emoji（如「你好 🎉」）
- **THEN** Jackson `writeValueAsBytes` 正确序列化为 UTF-8 JSON
- **AND** Consumer 反序列化后 content 字段保留 emoji 完整无损

#### Scenario: 含控制字符的消息序列化

- **WHEN** 消息 content 包含 `\n`、`\t`、`\r` 等控制字符
- **THEN** Jackson 正确转义为 `\n`、`\t`、`\r` 字面字符
- **AND** Consumer 反序列化后还原为原始控制字符

#### Scenario: 消息体超长保护

- **WHEN** 序列化后消息体超过 1MB
- **THEN** 记录 WARN 日志并标记 `x-msg-oversize` header
- **AND** 仍正常发送，由消费方决定是否处理

### Requirement: 配置化 MessageConverter

系统 SHALL 配置全局 `Jackson2JsonMessageConverter` 作为 RabbitMQ 默认 MessageConverter，替换 Java 序列化默认行为。

#### Scenario: 全局 Converter 配置生效

- **WHEN** 应用启动
- **THEN** `JacksonMqConfig` 注册 `Jackson2JsonMessageConverter` Bean
- **AND** 所有 `RabbitTemplate` 与 `@RabbitListener` 默认使用该 Converter

#### Scenario: 兼容现有消息格式

- **WHEN** 队列中存在历史消息（手写 JSON 格式）
- **THEN** Jackson 反序列化兼容标准 JSON 格式，无字段缺失
- **AND** 兼容失败的消息进入死信队列，不阻塞消费

### Requirement: 移除手写 escapeJson 逻辑

所有现存手写 `escapeJson` 字符串拼接逻辑 SHALL 被移除，统一走 Jackson 序列化。

#### Scenario: Producer 端移除手写拼接

- **WHEN** 任何 Producer 发送消息
- **THEN** 不再调用 `escapeJson` / `String.format` 拼 JSON
- **AND** 统一调用 `objectMapper.writeValueAsBytes(payload)` 序列化

#### Scenario: Consumer 端移除手写解析

- **WHEN** 任何 Consumer 接收消息
- **THEN** 不再手写 `split` / `substring` 解析 JSON
- **AND** 统一通过 `objectMapper.readTree(payload)` 或泛型反序列化
