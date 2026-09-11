## Why

平台当前虽然已建立 JWT 认证（`identity/user-auth`），但所有登录用户一律被 [JwtAuthenticationFilter](file:///d:/ai/api/src/main/java/com/ai/api/identity/security/JwtAuthenticationFilter.java) 硬编码授予单一 `ROLE_USER`，后端 [SecurityConfig](file:///d:/ai/api/src/main/java/com/ai/api/identity/config/SecurityConfig.java) 仅 `anyRequest().authenticated()`，无法区分用户权限等级。系统缺少集中的后台管理入口，需要一套 RBAC（基于角色的访问控制）体系，配合一个拥有最高权限的超级管理员角色，对「用户管理、角色管理、权限管理」进行 CRUD 运维，并为后续把各业务模块（对话、知识库、模型配置、Agent、客服）纳入权限管控打下基础。

## What Changes

- 新增 RBAC 领域能力（置于 `identity` 子域下，包名 `com.ai.api.identity.rbac.*`），引入「用户-角色-权限」三实体模型：
  - `sys_permission`：权限/资源表，区分「菜单」与「操作」两类，可 CRUD
  - `sys_role`：角色表，可 CRUD
  - `sys_user_role`：用户-角色关联（一个用户可多角色）
  - `sys_role_permission`：角色-权限关联（一个角色可拥有多权限）
  - 通过 Flyway 增量迁移建表，并写入内置种子数据
- 内置超级管理员角色：固定 `role_code=SUPER_ADMIN`，与普通角色共用同一张表承载，创建时绑定全部权限，且不可被删除/禁用/编辑角色编码。拥有它即可拥有系统内全部权限。
- 后端 RBAC 授权：
  - 管理端 REST 接口（`/api/v1/users` 管理、`/api/v1/roles`、`/api/v1/permissions`）支持 CRUD
  - `JwtAuthenticationFilter` / 身份构建时加载用户真实角色与权限集合写入 `SecurityContext` 的 authorities
  - 开启方法级安全 `@EnableMethodSecurity`，管理接口以 `@PreAuthorize("hasAuthority('xxx:create')")` 做操作级鉴权
  - 超级管理员 `SUPER_ADMIN` 自动放行全部权限点，不逐一绑定
  - `GET /users/me` 扩展返回当前用户的 `roles`、`permissions`，供前端渲染菜单/按钮
- 前端管理后台：
  - 新增「系统管理」下的三个菜单：用户管理、角色管理、权限管理，仅对拥有相应权限/角色的用户（超管默认可见全部）渲染并允许访问
  - 三个管理页均采用标准表格 + 弹窗 CRUD；角色页支持分配权限，用户页支持分配角色
  - 路由增加管理路由与权限守卫；`auth` store 的 `UserInfo` 扩展 `roles`/`permissions`；菜单按权限动态显隐
- `GET /users/me` 接口返回结构调整（新增 `roles`/`permissions` 字段），属向后兼容的增量字段；管理接口均为新增，无破坏性变更。

## Capabilities

### New Capabilities
- `identity/rbac-management`: RBAC 访问控制能力，覆盖权限/菜单资源管理、角色管理、用户-角色绑定、角色-权限绑定、内置超级管理员角色、方法级/路径级操作授权、前端管理菜单与动态权限渲染等行为规约

### Modified Capabilities
- `identity/user-auth`: 现有认证链路的身份信息需要携带角色与权限（`users/me` 返回 roles/permissions；认证过滤器从单一 `ROLE_USER` 改为加载真实角色/权限），属 spec 级行为变化

## Impact

- **后端代码**（`com.ai.api.identity.*`）：
  - 新增 `rbac/{entity,mapper,service,controller,dto}` 子包：`SysPermission`、`SysRole`、`SysUserRole`、`SysRolePermission` 实体与 Mapper；`PermissionService`、`RoleService`、`UserRoleService`；`PermissionController`、`RoleController`、`UserAdminController`
  - 修改 `SecurityConfig`：开启 `@EnableMethodSecurity`
  - 修改 `JwtAuthenticationFilter` / 身份加载逻辑：查询用户角色与权限写入 authorities
  - 修改 `AuthUserDetails`：由 `(userId, username)` 扩展为携带角色与权限集合
  - 修改 `UserController`：`/users/me` 返回新增 `roles`/`permissions`
  - 修改 `ErrorCode`：新增 RBAC 相关错误码（角色占用、权限码重复、超管保护、无权限等）
- **数据库**：新增 Flyway 脚本（如 `V20260906__add_rbac_tables.sql`），创建 4 张表 + 内置 `SUPER_ADMIN` 角色及权限种子数据（含管理端菜单权限点）
- **前端代码**（`ai-agent-web`）：
  - 新增 `src/api/system.ts`（用户/角色/权限管理接口）；修改 `src/api/auth.ts` 的 `UserInfo`
  - 新增 `src/views/system/{UserManageView,RoleManageView,PermissionManageView}.vue`
  - 修改 `src/router/index.ts` 增加管理路由与 `meta.permissions`；修改 `src/router/guards.ts` 增加权限校验
  - 修改 `src/layouts/DefaultLayout.vue` 支持菜单分组并按权限显隐「系统管理」菜单
  - 修改 `src/stores/auth.ts` 的 `UserInfo` 增加 `roles`/`permissions`
- **中间件**：引入「用户 → 权限」的 Redis 缓存（可选，用于降低每次请求的 DB 查询；权限变更时失效）
- **文档**：更新 `docs/api/auth.md`（users/me 变更）并新增 `docs/api/system.md`（管理接口）