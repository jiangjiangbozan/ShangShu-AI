## Purpose

为平台建立用户身份认证能力，覆盖注册、登录、登出、Token 刷新、忘记密码重置、会话管理、登录失败锁定、CSRF 防护与 XSS 过滤等行为，为后续业务模块提供统一的用户主体与会话上下文。

## ADDED Requirements

### Requirement: 用户注册

系统 SHALL 提供 `POST /api/v1/auth/register` 接口，接收 `username`、`email`、`password` 字段，对邮箱与用户名进行唯一性校验，密码通过 BCrypt（cost=10）+ 随机盐哈希后存储。注册成功后 SHALL 返回用户基本信息（不含密码相关字段），并自动签发首次登录 Token 对。MUST 在密码到达服务端前不做任何明文持久化。

#### Scenario: 正常注册

- **WHEN** 客户端 POST `/api/v1/auth/register`，请求体含合法 `username`（3-32 字符，字母数字下划线）、`email`（RFC 5322 合法）、`password`（≥8 位，含字母+数字）
- **THEN** 系统返回 HTTP 200 与 `Result{code:0, data:{userId, username, email, accessToken, refreshToken, expiresIn}}`
- **AND** 响应头 `Set-Cookie` 写入 `csrf-token`（HttpOnly=false，SameSite=Lax，TTL 2h）
- **AND** 数据库 `sys_user` 表新增一行，`password_hash` 为 BCrypt 哈希，`status=1`（正常），`fail_count=0`，`deleted=0`

#### Scenario: 邮箱已注册

- **WHEN** 客户端 POST `/api/v1/auth/register`，`email` 已存在于未逻辑删除用户中
- **THEN** 系统返回 `Result{code:409, message:"邮箱已被注册"}`，HTTP 状态码 200
- **AND** 不创建新用户记录，不签发 Token

#### Scenario: 密码强度不足

- **WHEN** 客户端 POST `/api/v1/auth/register`，`password` 长度 < 8 或仅字母或仅数字
- **THEN** 系统返回 `Result{code:400, message:"密码至少 8 位，须含字母与数字"}`，HTTP 200
- **AND** 不创建用户记录

### Requirement: 用户登录

系统 SHALL 提供 `POST /api/v1/auth/login` 接口，支持 `email` 或 `username` 任一字段 + `password` 登录。登录成功 SHALL 签发 JWT access token（默认 TTL 2h）与 refresh token（默认 TTL 7d），refresh token 的 JTI MUST 存入 Redis 用于黑名单失效控制。

#### Scenario: 正常登录

- **WHEN** 客户端 POST `/api/v1/auth/login`，请求体含合法 `account`（邮箱或用户名）与 `password`
- **THEN** 系统返回 `Result{code:0, data:{userId, username, email, accessToken, refreshToken, expiresIn:7200}}`
- **AND** `sys_user` 表 `last_login_at` 字段更新为当前时间，`fail_count` 重置为 0
- **AND** Redis 写入 `auth:refresh:{jti}` 键，TTL 7 天

#### Scenario: 密码错误

- **WHEN** 客户端 POST `/api/v1/auth/login`，`password` 与 `password_hash` BCrypt 校验不匹配
- **THEN** 系统返回 `Result{code:401, message:"账号或密码错误"}`，HTTP 200
- **AND** `fail_count` 自增 1，Redis `auth:fail:{account}` 自增并设置 TTL 15 分钟

#### Scenario: 账户锁定

- **WHEN** 同一账号连续 5 次登录失败，第 6 次登录请求到达
- **THEN** 系统返回 `Result{code:423, message:"账户已锁定，请 15 分钟后重试"}`，HTTP 200
- **AND** `sys_user.lock_until` 字段更新为 `now + 15min`，期间任何登录请求均拒绝

### Requirement: Token 刷新

系统 SHALL 提供 `POST /api/v1/auth/refresh` 接口，接收 `refreshToken` 字段。MUST 校验 refresh token 的签名、过期时间，并检查 Redis 中 JTI 是否在黑名单。刷新成功后 SHALL 签发新的 access + refresh token 对，并将旧 refresh token 加入黑名单。

#### Scenario: 正常刷新

- **WHEN** 客户端 POST `/api/v1/auth/refresh`，请求体含未过期且未失效的 `refreshToken`
- **THEN** 系统返回 `Result{code:0, data:{accessToken, refreshToken, expiresIn:7200}}`
- **AND** 旧 refresh token 的 JTI 加入 Redis 黑名单 `auth:refresh:blacklist:{jti}`，TTL 为旧 token 剩余有效期

#### Scenario: Refresh Token 失效

- **WHEN** 客户端 POST `/api/v1/auth/refresh`，`refreshToken` 已过期或其 JTI 已在黑名单中
- **THEN** 系统返回 `Result{code:401, message:"refresh token 已失效，请重新登录"}`，HTTP 200

### Requirement: 登出

系统 SHALL 提供 `POST /api/v1/auth/logout` 接口，要求请求头携带有效 access token。登出 SHALL 将当前 access token 的 JTI 加入 Redis 黑名单，TTL 为 token 剩余有效期，并将对应的 refresh token 一并失效。

#### Scenario: 正常登出

- **WHEN** 已认证用户 POST `/api/v1/auth/logout`，请求头 `Authorization: Bearer <token>` 有效
- **THEN** 系统返回 `Result{code:0, message:"登出成功"}`
- **AND** Redis 写入 `auth:access:blacklist:{jti}` 与 `auth:refresh:blacklist:{jti}` 两个键，TTL 为各自 token 剩余有效期

#### Scenario: 未认证登出

- **WHEN** 客户端 POST `/api/v1/auth/logout`，请求头无 `Authorization` 或 token 已失效
- **THEN** 系统返回 `Result{code:401, message:"未认证"}`，HTTP 200

### Requirement: 忘记密码

系统 SHALL 提供 `POST /api/v1/auth/password/forgot` 接口，接收 `email` 字段。系统 MUST 生成随机重置 token（32 字节，Base64URL 编码），存入 Redis `auth:reset:{token}`，TTL 30 分钟，并通过邮件服务发送重置链接至用户邮箱。为防止邮箱枚举攻击，无论邮箱是否注册，响应信息 MUST 相同。

#### Scenario: 邮箱已注册

- **WHEN** 客户端 POST `/api/v1/auth/password/forgot`，`email` 已注册
- **THEN** 系统返回 `Result{code:0, message:"重置链接已发送至邮箱"}`
- **AND** Redis 写入 `auth:reset:{token}`，值为 `userId`，TTL 30 分钟
- **AND** 邮件服务发送含 `https://{domain}/reset-password?token={token}` 链接的邮件

#### Scenario: 邮箱未注册

- **WHEN** 客户端 POST `/api/v1/auth/password/forgot`，`email` 未注册
- **THEN** 系统返回 `Result{code:0, message:"重置链接已发送至邮箱"}`（与已注册响应完全相同）
- **AND** 不发送邮件，不写 Redis 键

### Requirement: 重置密码

系统 SHALL 提供 `POST /api/v1/auth/password/reset` 接口，接收 `token` 与 `newPassword` 字段。MUST 校验 token 在 Redis 中存在且未过期，校验新密码强度，重置成功后 SHALL 删除 Redis 中的 token（单次有效），并将用户密码更新为新的 BCrypt 哈希。

#### Scenario: 正常重置

- **WHEN** 客户端 POST `/api/v1/auth/password/reset`，`token` 在 Redis 中存在，`newPassword` 满足强度要求
- **THEN** 系统返回 `Result{code:0, message:"密码重置成功"}`
- **AND** Redis `auth:reset:{token}` 键被删除
- **AND** `sys_user.password_hash` 与 `salt` 更新为新值
- **AND** 该用户所有 refresh token JTI 加入黑名单

#### Scenario: Token 失效

- **WHEN** 客户端 POST `/api/v1/auth/password/reset`，`token` 不在 Redis 中或已过期
- **THEN** 系统返回 `Result{code:401, message:"重置链接已失效，请重新申请"}`，HTTP 200

### Requirement: JWT 认证过滤器

系统 SHALL 在 Spring Security 过滤链中注册 `JwtAuthenticationFilter`，对所有非白名单路径的请求执行 JWT 校验。MUST 校验 access token 的签名（HS256）、过期时间、JTI 黑名单。校验成功 SHALL 将 `Authentication` 写入 `SecurityContext`，失败 SHALL 委托 `AuthEntryPoint` 返回 `Result{code:401}`。

#### Scenario: 有效 Token 访问受保护接口

- **WHEN** 客户端 GET `/api/v1/model/ping`，请求头 `Authorization: Bearer <valid-token>`
- **THEN** 请求通过过滤器，到达 Controller 执行业务
- **AND** `SecurityContext.getContext().getAuthentication()` 返回含 `userId`、`username` 的 `Authentication`

#### Scenario: 无 Token 访问受保护接口

- **WHEN** 客户端 GET `/api/v1/model/ping`，请求头无 `Authorization`
- **THEN** 请求被过滤器拦截，不到达 Controller
- **AND** 响应体为 `Result{code:401, message:"未认证"}`，HTTP 200

### Requirement: CSRF 防护

系统 SHALL 对所有非 GET 请求执行 Double-Submit Cookie CSRF 防护。客户端 MUST 在请求头 `X-CSRF-Token` 与 Cookie `csrf-token` 中携带相同的随机值，服务端校验两者一致且非空。登录与注册接口本身 MUST 豁免 CSRF 校验。

#### Scenario: CSRF 校验通过

- **WHEN** 客户端 POST `/api/v1/auth/refresh`，请求头 `X-CSRF-Token` 与 Cookie `csrf-token` 一致
- **THEN** 请求通过 CSRF 过滤器，进入业务处理

#### Scenario: CSRF Token 缺失或不一致

- **WHEN** 客户端 POST `/api/v1/auth/refresh`，请求头 `X-CSRF-Token` 缺失或与 Cookie 不一致
- **THEN** 系统返回 `Result{code:403, message:"CSRF 校验失败"}`，HTTP 200

### Requirement: XSS 防护

系统 SHALL 对所有用户输入字段执行 XSS 过滤，使用 AntiSamy（Policy：antisamy-slashdot.xml）清洗 HTML 标签与脚本。MUST 在参数绑定前生效，对注册、登录、忘记密码接口的 `username`、`email`、`password` 字段强制过滤。

#### Scenario: 输入含脚本标签

- **WHEN** 客户端 POST `/api/v1/auth/register`，`username` 字段含 `<script>alert(1)</script>`
- **THEN** AntiSamy 清洗后 `username` 变为 `alert(1)`，继续走业务校验
- **AND** 因 username 含非法字符（仅允许字母数字下划线），返回 `Result{code:400, message:"用户名格式非法"}`

#### Scenario: 输入合法

- **WHEN** 客户端 POST `/api/v1/auth/register`，`username` 字段为纯文本 `alice123`
- **THEN** AntiSamy 不改变输入，`alice123` 通过校验进入注册流程

### Requirement: 会话自动登录

系统 SHALL 支持 refresh token 自动登录机制。客户端在 access token 过期后 MUST 使用本地存储的 refresh token 调用 `/api/v1/auth/refresh` 获取新的 token 对，无需用户重新输入凭据。系统 SHALL 在 refresh token 也过期时强制要求重新登录。

#### Scenario: Access Token 过期自动刷新

- **WHEN** 客户端持有未过期的 refresh token，但 access token 已过期
- **THEN** 客户端调用 `/api/v1/auth/refresh` 成功获取新 token 对，继续访问受保护接口
- **AND** 旧 refresh token 立即失效（旋转机制）

#### Scenario: Refresh Token 也过期

- **WHEN** 客户端持有的 refresh token 已过期
- **THEN** `/api/v1/auth/refresh` 返回 401，客户端 MUST 引导用户重新登录
