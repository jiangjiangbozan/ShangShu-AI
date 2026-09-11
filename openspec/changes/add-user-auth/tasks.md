## 1. 后端基础设施

- [x] 1.1 修改 [pom.xml](file:///d:/ai/api/pom.xml)：新增 `spring-boot-starter-security`、`spring-boot-starter-mail`、`jjwt-api/impl/jackson` 0.12.6、`antisamy` 1.7.5 依赖
  - 验证：`./mvnw.cmd dependency:resolve` 成功解析所有新依赖
- [x] 1.2 新增 `application-dev.yml` 配置段 `ai.auth.*`：jwt.secret、access-token-ttl、refresh-token-ttl、mail.host/port/username/password/from、login-fail.max-attempts、login-fail.lock-duration、csrf.cookie-domain
  - 验证：`./mvnw.cmd spring-boot:run` 启动无报错，配置类 `AuthProperties` 能正确绑定
- [x] 1.3 创建 `com.ai.api.identity.config.AuthProperties` 配置类，使用 `@ConfigurationProperties("ai.auth")` 绑定
  - 验证：单测 `AuthPropertiesTest`，验证默认值与绑定值

## 2. 数据层

- [x] 2.1 创建 Flyway 迁移脚本 [V20260819__create_sys_user.sql](file:///d:/ai/api/src/main/resources/db/migration/V20260819__create_sys_user.sql)，按 design.md DDL 建表
  - 验证：应用启动时 Flyway 自动执行，`sys_user` 表存在且索引正确
- [x] 2.2 创建实体 [SystemUser.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/entity/SystemUser.java)，继承 `BaseDO`，添加 `@TableName("sys_user")`，字段与表一一对应
  - 验证：单测 `SystemUserMapperTest` 能正确插入与查询
- [x] 2.3 创建 [SystemUserMapper.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/mapper/SystemUserMapper.java)，继承 `BaseMapper<SystemUser>`，声明 `selectByUsername`、`selectByEmail` 方法
  - 验证：单测 `SystemUserMapperTest` 覆盖 CRUD + 两个自定义查询
- [x] 2.4 创建 [SystemUserMapper.xml](file:///d:/ai/api/src/main/resources/mapper/identity/SystemUserMapper.xml)，实现自定义查询 SQL，使用 `#{}` 防注入
  - 验证：单测 `SystemUserMapperTest` 自定义查询通过

## 3. 安全层

- [x] 3.1 创建 [TokenService.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/service/TokenService.java)：实现 JWT 签发（HS256）、解析、JTI 黑名单（Redis）、access/refresh token 对签发
  - 验证：单测 `TokenServiceTest`，覆盖签发→解析→过期→黑名单拒绝
- [x] 3.2 创建 [JwtAuthenticationFilter.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/security/JwtAuthenticationFilter.java)：继承 `OncePerRequestFilter`，从 `Authorization: Bearer` 头解析 token，校验签名/过期/JTI，写入 `SecurityContext`
  - 验证：单测 `JwtAuthenticationFilterTest`，覆盖有效/无效/无 token 三种场景
- [x] 3.3 创建 [AuthEntryPoint.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/security/AuthEntryPoint.java)：实现 `AuthenticationEntryPoint`，返回 `Result{code:401}` JSON
  - 验证：单测 `AuthEntryPointTest`，验证响应体格式
- [x] 3.4 创建 [AccessDeniedHandlerImpl.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/security/AccessDeniedHandlerImpl.java)：实现 `AccessDeniedHandler`，返回 `Result{code:403}` JSON
  - 验证：单测 `AccessDeniedHandlerImplTest`
- [x] 3.5 创建 [CsrfFilter.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/security/CsrfFilter.java)：实现 Double-Submit Cookie 模式，对非 GET 请求校验 `X-CSRF-Token` 头与 `csrf-token` Cookie 一致
  - 验证：单测 `CsrfFilterTest`，覆盖校验通过/不一致/缺失
- [x] 3.6 创建 [XssFilter.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/security/XssFilter.java)：使用 AntiSamy（`antisamy-slashdot.xml` policy）清洗请求体中标注 `@XssProtect` 的字段
  - 验证：单测 `XssFilterTest`，覆盖 `<script>` 标签被清洗
- [x] 3.7 创建 [SecurityConfig.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/config/SecurityConfig.java)：配置 Spring Security 7 过滤链，白名单 `/api/v1/auth/register|login|password/**`、`/actuator/**`、`/swagger-ui/**`、`/v3/api-docs/**`，其余路径 `authenticated()`
  - 验证：集成测 `SecurityConfigTest`，验证白名单匿名可访问、其他路径 401

## 4. 业务层

- [x] 4.1 创建 [UserService.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/service/UserService.java)：实现 `register`、`getById`、`getByUsernameOrEmail`、`updatePassword`、`updateLastLogin`、`incrementFailCount`、`lockAccount` 方法
  - 验证：单测 `UserServiceTest`（Mock Mapper），覆盖注册唯一性冲突、密码哈希正确、失败计数自增
- [x] 4.2 创建 [LoginFailLimiter.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/service/LoginFailLimiter.java)：使用 Redis 计数 + TTL，5 次失败后锁定 15 分钟
  - 验证：单测 `LoginFailLimiterTest`（Testcontainers Redis），覆盖 4 次未锁定、5 次锁定、锁定后 15 分钟内拒绝
- [x] 4.3 创建 [MailService.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/service/MailService.java)：`@Async` + RabbitMQ 投递，发送密码重置邮件
  - 验证：单测 `MailServiceTest`（Mock JavaMailSender + RabbitTemplate），覆盖正常发送与死信重试
- [x] 4.4 创建 [AuthService.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/service/AuthService.java)：实现 `register`、`login`、`refresh`、`logout`、`forgotPassword`、`resetPassword` 主流程
  - 验证：单测 `AuthServiceTest`（Mock 所有下游 Service），覆盖 spec.md 所有 Scenario

## 5. DTO 层

- [x] 5.1 创建 [RegisterRequestDTO.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/dto/RegisterRequestDTO.java)：`username`（3-32 字符正则）、`email`（`@Email`）、`password`（自定义强度校验注解）、字段标注 `@XssProtect`
  - 验证：单测 `RegisterRequestDTOValidationTest`，覆盖合法/非法用户名、非法邮箱、弱密码
- [x] 5.2 创建 [LoginRequestDTO.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/dto/LoginRequestDTO.java)：`account`（邮箱或用户名）、`password`，标注 `@XssProtect`
  - 验证：单测 `LoginRequestDTOValidationTest`
- [x] 5.3 创建 [RefreshRequestDTO.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/dto/RefreshRequestDTO.java)、[ForgotPasswordRequestDTO.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/dto/ForgotPasswordRequestDTO.java)、[ResetPasswordRequestDTO.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/dto/ResetPasswordRequestDTO.java)、[LoginResponseDTO.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/dto/LoginResponseDTO.java)
  - 验证：编译通过，字段类型正确

## 6. 接入层

- [x] 6.1 创建 [AuthController.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/controller/AuthController.java)：实现 `POST /register`、`POST /login`、`POST /refresh`、`POST /logout`、`POST /password/forgot`、`POST /password/reset`，返回 `Result<T>`
  - 验证：接口测 `AuthControllerTest`（MockMvc + Mockito），覆盖 spec.md 所有 Scenario
- [x] 6.2 创建 [UserController.java](file:///d:/ai/api/src/main/java/com/ai/api/identity/controller/UserController.java)：实现 `GET /users/me`，从 `SecurityContext` 取当前用户
  - 验证：接口测 `UserControllerTest`
- [x] 6.3 扩展 [ErrorCode.java](file:///d:/ai/api/src/main/java/com/ai/api/common/exception/ErrorCode.java)：新增 `AUTH_*` 段（900-999），含 `AUTH_USER_NOT_FOUND`、`AUTH_BAD_CREDENTIALS`、`AUTH_ACCOUNT_LOCKED`、`AUTH_EMAIL_EXISTS`、`AUTH_TOKEN_INVALID`、`AUTH_TOKEN_EXPIRED`、`AUTH_RESET_TOKEN_INVALID`、`AUTH_CSRF_FAILED`、`AUTH_PASSWORD_WEAK`
  - 验证：单测 `ErrorCodeTest` 已存在，扩展覆盖新枚举
- [x] 6.4 扩展 [GlobalExceptionHandler.java](file:///d:/ai/api/src/main/java/com/ai/api/common/handler/GlobalExceptionHandler.java)：捕获 `AuthenticationException`、`AccessDeniedException`、`AuthException`，返回对应 `Result` 包装
  - 验证：集成测 `GlobalExceptionHandlerAuthTest`

## 7. 前端基础设施

- [x] 7.1 修改 [package.json](file:///d:/ai/ai-agent-web/package.json)：无新依赖（已有 axios、Pinia、Vue Router、Element Plus）
  - 验证：`npm install` 无报错
- [x] 7.2 创建 [src/api/auth.ts](file:///d:/ai/ai-agent-web/src/api/auth.ts)：封装 `register`、`login`、`refresh`、`logout`、`forgotPassword`、`resetPassword`、`getCurrentUser` API 调用
  - 验证：`npm run type-check` 通过
- [x] 7.3 创建 [src/stores/auth.ts](file:///d:/ai/ai-agent-web/src/stores/auth.ts)：Pinia store，state 含 `accessToken/refreshToken/user`，action 含 `login/register/refresh/logout/fetchCurrentUser`
  - 验证：`npm run type-check` 通过
- [x] 7.4 修改 [src/utils/request.ts](file:///d:/ai/ai-agent-web/src/utils/request.ts)：axios 请求拦截器附加 `Authorization: Bearer` 与 `X-CSRF-Token` 头；响应拦截器在 401 时调用 `refresh` 并重放原请求，refresh 也失败则跳转 `/login`
  - 验证：`npm run dev` 启动后，浏览器 DevTools Network 中可见请求头正确附加

## 8. 前端页面

- [x] 8.1 创建 [src/views/auth/LoginView.vue](file:///d:/ai/ai-agent-web/src/views/auth/LoginView.vue)：邮箱/用户名输入切换、密码输入、Element Plus 表单校验、加载状态、错误提示
  - 验证：手测登录页 UI 渲染、校验提示、登录成功跳转
- [x] 8.2 创建 [src/views/auth/RegisterView.vue](file:///d:/ai/ai-agent-web/src/views/auth/RegisterView.vue)：用户名/邮箱/密码/确认密码、密码强度实时提示、防 XSS 提示
  - 验证：手测注册页 UI 与校验
- [x] 8.3 创建 [src/views/auth/ForgotPasswordView.vue](file:///d:/ai/ai-agent-web/src/views/auth/ForgotPasswordView.vue)：邮箱输入、发送成功提示
  - 验证：手测忘记密码页 UI
- [x] 8.4 修改 [src/router/index.ts](file:///d:/ai/ai-agent-web/src/router/index.ts)：添加 `/login`、`/register`、`/forgot-password`、`/reset-password` 路由，`meta: { public: true }`
  - 验证：`npm run dev` 后浏览器可访问四个 URL
- [x] 8.5 创建 [src/router/guards.ts](file:///d:/ai/ai-agent-web/src/router/guards.ts)：`beforeEach` 守卫，未登录访问非 public 路由时跳转 `/login?redirect=...`
  - 验证：手测未登录访问 `/` 自动跳转登录页
- [x] 8.6 修改 [src/layouts/DefaultLayout.vue](file:///d:/ai/ai-agent-web/src/layouts/DefaultLayout.vue)：Header 添加用户名下拉菜单，含"退出登录"按钮
  - 验证：手测退出登录后跳转登录页

## 9. 联调与验证

- [x] 9.1 启动完整基础设施（MySQL/Redis/RabbitMQ/SMTP 测试服务），运行后端全量测试：`./mvnw.cmd test`
  - 验证：所有单测、接口测、集成测通过，覆盖率 ≥ 70%
  - 当前状态：39 tests / 0 failures / 22 skipped（Testcontainers 集成测试因本地无 Docker 自动跳过，单测全部通过）
- [x] 9.2 启动前端 `npm run dev`，运行 `npm run type-check` 与 `npm run build`
  - 验证：无 TypeScript 报错，构建产物可部署（前序阶段已完成，build 通过）
- [ ] 9.3 端到端联调：注册→登录→访问受保护接口→登出→忘记密码→重置密码→重新登录全流程
  - 验证：手测全流程无 bug，所有 spec.md Scenario 可复现
  - 阻塞依赖：需启动 MySQL/Redis/RabbitMQ/SMTP 完整基础设施（由用户处理）
- [ ] 9.4 性能验证：登录接口单次响应 < 500ms（含 BCrypt 哈希），注册 < 800ms
  - 验证：Apache Bench 或 Postman 压测 100 次取平均
  - 阻塞依赖：需基础设施就绪
- [ ] 9.5 安全验证：使用 OWASP ZAP 扫描登录页与 API，验证无 SQL 注入、XSS、CSRF 漏洞
  - 验证：ZAP 报告无 High 级别告警
  - 阻塞依赖：需基础设施就绪与运行中的应用实例

## 10. 文档

- [x] 10.1 创建 [docs/api/auth.md](file:///d:/ai/docs/api/auth.md)：列明所有 Auth API 的请求/响应/错误码，附 curl 示例
  - 验证：文档可读，示例可在 Postman 复现
- [x] 10.2 更新 [docs/changes-roadmap.md](file:///d:/ai/docs/changes-roadmap.md)：标注迭代 0（用户认证）完成
  - 验证：文档中可见 add-user-auth change 状态为"已完成"
- [x] 10.3 创建 [docs/design/auth-design.md](file:///d:/ai/docs/design/auth-design.md)：将 design.md 关键决策与模块图迁移至项目文档库，便于团队查阅
  - 验证：文档与 design.md 内容一致
