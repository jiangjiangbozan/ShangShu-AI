## 1. 后端依赖与配置（数据层 → 能力层）

- [x] 1.1 [手测] 更新 `d:\ai\api\pom.xml`：引入 `mybatis-plus-spring-boot3-starter`、`flyway-core`、`flyway-mysql`、`springdoc-openapi-starter-webmvc-ui`、`spring-boot-starter-actuator`、`spring-ai-starter-model-openai`（DeepSeek/Qwen 兼容）、`spring-ai-starter-model-ollama`、`p6spy`（dev SQL 打印）
- [x] 1.2 [手测] 删除 `src/main/resources/application.properties`，创建 `application.yml` + `application-dev.yml` + `application-prod.yml`，配置数据源、Redis、ES、RabbitMQ、Spring AI、MyBatis-Plus、Actuator、springdoc（见 design.md D6/D7）
- [x] 1.3 [手测] 创建 `src/main/resources/db/migration/V20260818__init.sql`，建库 `ai_agent` + `sys_model_config`、`sys_llm_log` 两张表（见 design.md D6）
- [x] 1.4 [手测] 配置 `MybatisPlusInterceptor`（`PaginationInnerInterceptor` + `OptimisticLockerInnerInterceptor`）于 `com.ai.api.data.config.MybatisPlusConfig`
- [x] 1.5 [手测] 在 `ApiApplication.java` 添加 `@MapperScan("com.ai.api.**.mapper")` 与 `@EnableAsync`、`@EnableScheduling`

## 2. 后端 common 子包（统一响应/异常/TraceId/CORS）

- [x] 2.1 [单测] 实现 `com.ai.api.common.result.Result<T>`（code/message/data/traceId）与 `PageResult<T>`
- [x] 2.2 [单测] 实现 `com.ai.api.common.exception.ErrorCode` 枚举（按模块号分段，见 [doc-templates.md](file:///d:/ai/docs/doc-templates.md) §5.5）与 `BusinessException`
- [x] 2.3 [单测] 实现 `com.ai.api.common.handler.GlobalExceptionHandler`（处理 `BusinessException`、`MethodArgumentNotValidException`、`ConstraintViolationException`、`Exception`）
- [x] 2.4 [单测] 实现 `com.ai.api.common.filter.TraceIdFilter`（生成/继承 `X-Trace-Id`，写入 MDC，配置 `TaskDecorator` 传递到 `@Async` 与 RabbitMQ 消费者）
- [x] 2.5 [单测] 实现 `com.ai.api.common.config.CorsConfig`（允许 `http://localhost:5173`，可配置化）
- [x] 2.6 [单测] 实现 `com.ai.api.common.config.JacksonConfig`（统一日期格式、Long 转字符串避免前端精度丢失）

## 3. 后端 data 子包（MyBatis 示例）

- [x] 3.1 [单测] 创建 `com.ai.api.data.entity.SysModelConfig`（`@TableName("sys_model_config")`、`@TableId`、`@TableField`）
- [x] 3.2 [单测] 创建 `com.ai.api.data.entity.SysLlmLog`
- [x] 3.3 [单测] 创建 `com.ai.api.data.mapper.SysModelConfigMapper`（继承 `BaseMapper<SysModelConfig>`）
- [x] 3.4 [单测] 创建 `com.ai.api.data.mapper.SysLlmLogMapper`
- [x] 3.5 [单测] 创建 `resources/mapper/system/SysModelConfigMapper.xml`（含按 provider 查询、按 modelId 查询的示例复杂查询）
- [x] 3.6 [单测] 创建 `resources/mapper/system/SysLlmLogMapper.xml`
- [x] 3.7 [单测] 编写 `SysModelConfigMapperTest`、`SysLlmLogMapperTest`（Testcontainers MySQL），验证 CRUD 与分页

## 4. 后端 model 子包（大模型调用通路）

- [x] 4.1 [单测] 实现 `com.ai.api.model.config.ModelConfig`（`@ConfigurationProperties("ai.model")`，读取 DeepSeek/Qwen/Ollama 配置）
- [x] 4.2 [单测] 实现 `com.ai.api.model.config.ChatClientFactory`（为每个 provider 注册 `OpenAiChatModel` + `ChatClient` Bean，使用 `@Qualifier` 区分）
- [x] 4.3 [单测] 实现 `com.ai.api.model.service.ModelPingService`：按 `modelId` 选择 `ChatClient`，调用 `.prompt().user(prompt).call().content()`，返回 `ModelPingResult(content, modelId, latencyMs, totalTokens)`
- [x] 4.4 [单测] 实现 `com.ai.api.model.service.LlmLogService`：通过 RabbitMQ `ai.llm.log` 异步写入 `sys_llm_log`（含 traceId、prompt 截断、response 截断）
- [x] 4.5 [单测] 配置 `com.ai.api.data.mq.LlmLogConsumer`（消费 `q.llm.log` 并调用 `SysLlmLogMapper.insert`）
- [x] 4.6 [接口测] 实现 `com.ai.api.model.controller.ModelPingController`：`POST /api/v1/model/ping`，入参 `{modelId, prompt}`，调用 `ModelPingService`，返回 `Result<ModelPingResult>`，添加 `@Tag`、`@Operation` Swagger 注解
- [x] 4.7 [接口测] 编写 `ModelPingControllerTest`（MockMvc），验证 200 响应与错误码

## 5. 后端工程规范

- [x] 5.1 [手测] 添加 `api/checkstyle.xml`，在 pom 中绑定 `validate` 阶段
- [x] 5.2 [手测] 添加 spotless-maven-plugin，绑定 `compile` 阶段（格式化 Java）
- [x] 5.3 [手测] 在 `api/src/main/resources/logback-spring.xml` 配置 JSON 日志输出（含 traceId）
- [x] 5.4 [手测] 编写 `api/README.md`：包含启动步骤、环境变量、Docker 依赖、Swagger 入口

## 6. 前端基础完善

- [x] 6.1 [手测] 更新 `d:\ai\ai-agent-web\vite.config.ts`：添加 `server.proxy`（`/api`、`/actuator` 代理到 `http://localhost:8080`）
- [x] 6.2 [手测] 创建 `src/utils/request.ts`：axios 实例 + 拦截器（注入 `X-Trace-Id`、统一处理 `Result<T>`、错误提示）
- [x] 6.3 [手测] 创建 `src/api/model.ts`：封装 `pingModel(modelId, prompt)` 接口
- [x] 6.4 [手测] 创建 `src/layouts/DefaultLayout.vue`：Element Plus 侧边栏 + 顶栏 + 主区域布局
- [x] 6.5 [手测] 更新 `src/router/index.ts`：注册路由 `/`（重定向到 `/model-test`）、`/model-test`（模型测试页）
- [x] 6.6 [手测] 创建 `src/views/model-test/Index.vue`：输入框（modelId + prompt）+ 按钮 + 响应展示区
- [x] 6.7 [手测] 更新 `src/App.vue`：使用 `<router-view>` 替换占位内容
- [x] 6.8 [手测] 添加 husky + lint-staged + commitlint（`@commitlint/config-conventional`）

## 7. 联调与验证

- [ ] 7.1 [手测] 启动后端：`mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- [ ] 7.2 [手测] 启动前端：`npm run dev`
- [ ] 7.3 [手测] 浏览器访问 `http://localhost:5173/model-test`，输入 `modelId=deepseek-chat` 与 `prompt=你好`，看到模型响应
- [ ] 7.4 [手测] 验证 `http://localhost:8080/actuator/health` 返回 UP
- [ ] 7.5 [手测] 验证 `http://localhost:8080/swagger-ui.html` 可访问，列出 `/api/v1/model/ping`
- [ ] 7.6 [手测] 验证日志包含 traceId，且 `sys_llm_log` 表有对应记录
- [ ] 7.7 [手测] 编写 `d:\ai\docs\test-reports\bootstrap-test.md` 测试报告

## 8. 文档与知识库

- [x] 8.1 [手测] 更新 `api/README.md` 与 `ai-agent-web/README.md`
- [x] 8.2 [手测] 在 `d:\ai\docs\kb\` 下创建技术栈使用指南（见 [training-plan.md](file:///d:/ai/docs/training-plan.md)）
- [x] 8.3 [手测] 在 `d:\ai\docs\kb\decisions/ADR-001-mybatis-as-dal.md` 记录 MyBatis 决策
- [x] 8.4 [手测] 在 `d:\ai\docs\kb\decisions/ADR-002-single-module-bootstrap.md` 记录单模块决策
- [x] 8.5 [手测] 创建 `d:\ai\docs\dev-log\` 开发日志目录与首篇 `2026-08-18.md`
