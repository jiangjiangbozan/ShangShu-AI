## Purpose

定义项目脚手架的基础约定：后端统一响应、异常处理、TraceId 链路、MyBatis 数据访问层、配置管理、CORS、健康检查等，作为所有业务模块的统一基础设施。

## ADDED Requirements

### Requirement: 统一响应格式
所有 REST API 接口 SHALL 返回统一的 `Result<T>` 结构，包含 code、message、data、traceId 四个字段；成功响应 code 必须为 0，业务异常使用 5 位数字错误码并按模块号分段。

#### Scenario: 成功响应
- **WHEN** 客户端调用任一业务接口且处理成功
- **THEN** 返回 HTTP 200，body 为 `{"code":0,"message":"success","data":<业务数据>,"traceId":"<uuid>"}`
- **AND** `traceId` 与请求头 `X-Trace-Id` 一致（若客户端传入）或为服务端生成的新 UUID

#### Scenario: 业务异常响应
- **WHEN** 业务逻辑抛出 `BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND)`
- **THEN** 返回 HTTP 200，body 为 `{"code":20401,"message":"会话不存在","data":null,"traceId":"<uuid>"}`
- **AND** 错误码 20401 属于对话模块（模块号 20）的错误码范围

#### Scenario: 系统异常响应
- **WHEN** 发生未捕获的运行时异常（如 NullPointerException）
- **THEN** 返回 HTTP 500，body 为 `{"code":500,"message":"系统繁忙","data":null,"traceId":"<uuid>"}`
- **AND** 异常堆栈写入日志（含 traceId），不暴露给客户端

### Requirement: 全局异常处理
系统 SHALL 通过 `@RestControllerAdvice` 全局异常处理器捕获 `BusinessException`、`MethodArgumentNotValidException`、`ConstraintViolationException` 及通用 `Exception`，并转换为统一响应格式。

#### Scenario: 参数校验失败
- **WHEN** 客户端提交的 DTO 触发 `@Valid` 校验失败
- **THEN** 返回 code=10001，message 列出所有字段错误（中英文均可）
- **AND** HTTP 状态码为 200（业务约定，统一用 code 区分）

#### Scenario: 未知异常兜底
- **WHEN** 抛出非 `BusinessException` 的异常
- **THEN** 全局异常处理器捕获并记录完整堆栈到日志
- **AND** 返回给客户端的 message 为"系统繁忙"，不包含敏感堆栈信息

### Requirement: TraceId 全链路追踪
系统 SHALL 通过 `TraceIdFilter` 在每个 HTTP 请求入口生成或继承 `X-Trace-Id`，并写入 MDC（Mapped Diagnostic Context），所有日志输出 SHALL 包含该 traceId。

#### Scenario: 客户端未传 traceId
- **WHEN** 客户端请求未携带 `X-Trace-Id` 头
- **THEN** 服务端生成新的 UUID 作为 traceId
- **AND** 响应头 `X-Trace-Id` 返回该值
- **AND** 本次请求所有日志行均包含该 traceId

#### Scenario: 客户端传入 traceId
- **WHEN** 客户端请求携带合法 `X-Trace-Id` 头
- **THEN** 服务端沿用该值，不重新生成
- **AND** 该 traceId 贯穿后续 MyBatis、Spring AI、Redis、ES、MQ 调用日志

### Requirement: MyBatis 数据访问层（团队强制要求）
系统 SHALL 使用 MyBatis 作为后端数据访问层统一规范；所有业务模块的持久化 MUST 通过 MyBatis Mapper 接口 + XML 映射文件实现，禁止使用其他 ORM 框架（如 JPA/Hibernate）。

#### Scenario: Mapper 标准结构
- **WHEN** 新增业务实体（如 `ChatSession`）
- **THEN** 必须在对应模块包下创建 `<Entity>Mapper.java` 接口（继承 `BaseMapper<Entity>`，使用 MyBatis-Plus）
- **AND** 对应的 XML 映射文件位于 `src/main/resources/mapper/<module>/<Entity>Mapper.xml`
- **AND** 实体类使用 `@TableName` 注解映射数据库表，主键使用 `@TableId(type=IdType.AUTO)`

#### Scenario: 复杂查询使用 XML
- **WHEN** 单表 CRUD 之外的多表关联、聚合查询
- **THEN** MUST 在 XML 映射文件中编写 SQL，使用 `#{param}` 参数化（禁止字符串拼接，防 SQL 注入）
- **AND** Mapper 接口方法使用 `@Param` 注解或 DTO 对象传参

#### Scenario: 分页查询
- **WHEN** 业务需要分页查询
- **THEN** 使用 MyBatis-Plus 的 `IPage<T>` + `Page<T>` 分页对象
- **AND** 配置 `MybatisPlusInterceptor`（`PaginationInnerInterceptor`）生效

### Requirement: 数据库迁移管理
系统 SHALL 使用 Flyway 管理数据库 schema 变更，迁移脚本 MUST 位于 `src/main/resources/db/migration/`，命名格式为 `V<yyyyMMdd>__<description>.sql`，按模块分目录组织。

#### Scenario: 首次启动自动迁移
- **WHEN** 应用首次启动且数据库为空
- **THEN** Flyway 自动执行 `V20260818__init.sql` 创建数据库与基础表
- **AND** `flyway_schema_history` 表记录迁移历史
- **AND** 应用正常启动无报错

#### Scenario: 迁移失败回滚
- **WHEN** 某个迁移脚本语法错误导致执行失败
- **THEN** 应用启动失败并明确报错
- **AND** 已执行的迁移保持原状（Flyway 不自动回滚 DDL）

### Requirement: 配置管理
系统 SHALL 使用 `application.yml` 作为主配置文件，支持 `dev`/`prod` profile，敏感信息 MUST 通过环境变量注入（`${VAR}` 占位符）。

#### Scenario: dev profile
- **WHEN** 启动时设置 `SPRING_PROFILES_ACTIVE=dev`
- **THEN** 加载 `application-dev.yml`，连接本地 MySQL/Redis/ES/RabbitMQ
- **AND** 日志级别为 DEBUG，开启 SQL 打印

#### Scenario: prod profile
- **WHEN** 启动时设置 `SPRING_PROFILES_ACTIVE=prod`
- **THEN** 加载 `application-prod.yml`
- **AND** 数据库密码、API Key 通过环境变量注入
- **AND** 日志级别为 INFO，关闭 SQL 打印

### Requirement: CORS 跨域
系统 SHALL 配置全局 CORS，允许前端开发域名（默认 `http://localhost:5173`）跨域访问，允许的方法、头、凭据 MUST 可配置化。

#### Scenario: 预检请求
- **WHEN** 前端发起跨域 POST 请求前先发送 OPTIONS 预检
- **THEN** 返回 HTTP 200，包含 `Access-Control-Allow-Origin: http://localhost:5173`
- **AND** 包含 `Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS`
- **AND** 包含 `Access-Control-Allow-Headers: Authorization,Content-Type,X-Trace-Id`

#### Scenario: 凭据携带
- **WHEN** 前端携带 `Authorization: Bearer <token>` 请求
- **THEN** `Access-Control-Allow-Credentials: true`
- **AND** 后端能正确读取 token

### Requirement: 健康检查与监控
系统 SHALL 暴露 `/actuator/health` 健康检查端点，并启用 `/actuator/prometheus` 指标端点供 Prometheus 抓取。

#### Scenario: 健康检查
- **WHEN** 访问 `GET /actuator/health`
- **THEN** 返回 HTTP 200，body 为 `{"status":"UP"}`
- **AND** 包含 MySQL、Redis、ES、RabbitMQ 各组件健康状态

#### Scenario: 指标暴露
- **WHEN** Prometheus 访问 `GET /actuator/prometheus`
- **THEN** 返回 HTTP 200，Content-Type 为 `text/plain; version=0.0.4; charset=utf-8`
- **AND** 包含 `http_server_requests_seconds_*`、`jvm_*` 等指标

### Requirement: API 文档
系统 SHALL 通过 springdoc-openapi 暴露 Swagger UI（`/swagger-ui.html`）与 OpenAPI JSON（`/v3/api-docs`），所有 Controller MUST 添加 `@Tag`、`@Operation` 注解。

#### Scenario: 访问 Swagger UI
- **WHEN** 浏览器访问 `http://localhost:8080/swagger-ui.html`
- **THEN** 重定向到 Swagger UI 页面
- **AND** 列出所有已实现的接口分组（按 Controller `@Tag` 分组）

#### Scenario: OpenAPI JSON
- **WHEN** 访问 `GET /v3/api-docs`
- **THEN** 返回符合 OpenAPI 3 规范的 JSON
- **AND** 包含所有接口的路径、方法、请求体、响应体定义
