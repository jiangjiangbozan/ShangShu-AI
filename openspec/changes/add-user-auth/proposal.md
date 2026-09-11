## Why

平台当前无身份认证体系，所有 `/api/v1/**` 接口均为匿名可访问，无法支撑对话历史、知识库、计费、配额等业务模块所需的"用户"概念。作为平台首个涉及用户态的 change，必须先建立认证基座，后续所有业务模块（chat、rag、agent、billing）均依赖此能力进行资源归属与权限隔离。同时，平台对公网开放 AI 能力的安全前置要求（密码加密存储、登录限流、CSRF 防护、会话管理）必须在开放注册前一次性建立，避免后续改造成本。

## What Changes

- 新增 `user`（用户主体）+ `auth`（认证会话）两个领域模块，统一置于 `identity` 子域下，包名 `com.ai.api.identity.*`
- 新增 `sys_user` 数据表（用户名/邮箱/密码哈希/盐/账户状态/失败次数/锁定截止时间/审计字段），通过 Flyway 增量迁移 `V20260819__create_sys_user.sql`
- 新增注册接口 `POST /api/v1/auth/register`：支持用户名 + 邮箱 + 密码，邮箱唯一性校验，密码强度校验（≥8 位，字母+数字），密码使用 BCrypt（cost=10）+ 随机盐哈希存储
- 新增登录接口 `POST /api/v1/auth/login`：支持邮箱或用户名 + 密码，签发 JWT（access token + refresh token）
- 新增"忘记密码"接口链路：`POST /api/v1/auth/password/forgot`（发送重置链接邮件）→ `POST /api/v1/auth/password/reset`（携带 token 重置密码）；重置 token 使用 Redis 存储，TTL 30 分钟，单次有效
- 新增会话管理：access token 默认 2h，refresh token 默认 7d；提供 `POST /api/v1/auth/refresh`、`POST /api/v1/auth/logout`
- 新增 JWT 认证过滤器 `JwtAuthenticationFilter` + `SecurityContextRepository`，白名单路径含注册/登录/忘记密码/健康检查/Swagger
- 新增登录失败次数限制：连续 5 次失败锁定账户 15 分钟（Redis 计数 + TTL）
- 新增 CSRF 防护：基于 `DoubleSubmitCookie` 模式，对非 GET 请求校验 `X-CSRF-Token` Header 与 Cookie 一致
- 新增 XSS 防护：全局响应 `Content-Type: application/json; charset=utf-8` + 输入参数 `@XssProtect` 注解式过滤（AntiSamy）
- **BREAKING**：现有 `/api/v1/model/ping` 等匿名接口将强制要求 access token，调用方需在请求头携带 `Authorization: Bearer <token>`
- 前端 `ai-agent-web` 新增登录/注册/忘记密码三个页面 + 全局 axios 拦截器自动附加 token + 401 自动跳转登录

## Capabilities

### New Capabilities
- `identity/user-auth`: 用户认证能力，覆盖注册、登录、登出、Token 刷新、忘记密码重置、会话管理、登录失败锁定、CSRF 防护、XSS 过滤等行为规约

### Modified Capabilities
（无现有 spec，本次为首个变更）

## Impact

- **后端代码**：
  - 新增包 `com.ai.api.identity.{controller,service,repository,entity,config,security}`
  - 新增 Flyway 脚本 `V20260819__create_sys_user.sql`
  - 新增 `JwtAuthenticationFilter`、`AuthEntryPoint`、`AccessDeniedHandler`
  - 修改 [pom.xml](file:///d:/ai/api/pom.xml)：引入 `spring-boot-starter-security`、`spring-security-oauth2-resource-server`（JWT）、`jjwt`、`spring-boot-starter-mail`、`antisamy`
  - 修改 [application.yml](file:///d:/ai/api/src/main/resources/application.yml)：新增 `ai.auth.*` 配置段
  - 修改现有所有 Controller：路径从 `/api/v1/**` 前置 JWT 校验（model-ping 例外）
- **前端代码**：
  - 新增 `src/views/auth/{LoginView,RegisterView,ForgotPasswordView}.vue` + `src/router/guards.ts`（路由守卫）+ `src/stores/auth.ts`（Pinia）+ `src/api/auth.ts`
  - 修改 `src/api/http.ts`：请求拦截附加 `Authorization` 头，响应拦截 401 跳转登录
  - 修改 `src/router/index.ts`：添加 `/login`、`/register`、`/forgot-password` 路由
- **数据库**：新增 `sys_user` 表，字段含 `username`、`email`、`password_hash`、`salt`、`status`、`fail_count`、`lock_until`、`last_login_at` + `BaseDO` 审计字段
- **中间件**：Redis 新增 key 命名空间 `auth:fail:{username}`（登录失败计数）、`auth:reset:{token}`（重置 token）、`auth:refresh:{jti}`（refresh token 黑名单）
- **外部依赖**：Spring Security 7（Spring Boot 4 配套）、jjwt 0.13、commons-email / spring-boot-starter-mail、AntiSamy 1.7
- **文档**：新增 `docs/api/auth.md` 接口文档；更新 `docs/changes-roadmap.md` 标注迭代 1 完成
