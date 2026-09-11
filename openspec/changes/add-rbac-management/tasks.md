## 1. 数据层（Flyway 迁移）

- [x] 1.1 新增 `V20260906__add_rbac_tables.sql`：创建 `sys_permission`（含 parent_id/code/name/type/path/icon/sort/status + BaseDO，`uk_permission_code(code,deleted)`）、`sys_role`（code/name/remark/status + BaseDO，`uk_role_code(code,deleted)`）、`sys_user_role`（user_id/role_id + BaseDO，`uk_user_role(user_id,role_id,deleted)`，`idx_role_id`）、`sys_role_permission`（role_id/permission_id + BaseDO，`uk_role_perm(role_id,permission_id,deleted)`，`idx_permission_id`） — 接口测：`SHOW TABLES` 确认 4 表及唯一索引存在
- [x] 1.2 迁移脚本插入权限种子数据（`system:user:menu/role:menu/permission:menu` 三个菜单权限 + `system:user:create/update/delete/assign`、`system:role:create/update/delete/assign`、`system:permission:create/update/delete` 操作权限） — 手测：`SELECT` 校验权限点齐全
- [x] 1.3 迁移脚本插入 `SUPER_ADMIN` 角色并为该角色全量绑定所有权限点 — 手测：`sys_user_role`、`sys_role_permission` 关联正确

## 2. 后端能力层：实体与 Mapper

- [x] 2.1 新增 `com.ai.api.identity.rbac.entity`：`SysPermission`、`SysRole`、`SysUserRole`、`SysRolePermission`（继承 `BaseDO`，`@TableName`+`@TableLogic`） — 单测：字段映射编译通过
- [x] 2.2 新增 `com.ai.api.identity.rbac.mapper`：`SysPermissionMapper`、`SysRoleMapper`、`SysUserRoleMapper`、`SysRolePermissionMapper`（继承 `BaseMapper<T>`，@MapperScan 自动扫描） — 单测：注入成功

## 3. 后端业务层：Service 与授权

- [x] 3.1 `ErrorCode` 追加 RBAC 错误码（950 角色不存在、951 角色编码重复、952 权限不存在、953 权限编码重复、954 权限仍被引用、956 超级角色受保护、957 无法移除最后一名超管） — 单测：`ErrorCodeTest` 覆盖新增枚举
- [x] 3.2 新增 `RbacPermissionService.loadAuthorities(userId)`：查询用户角色与权限，含 `SUPER_ADMIN` 时返回全部权限码；接入 Redis 缓存 `rbac:user:perms:{userId}`（TTL 5min），未命中回源 DB — 单测：mock RBAC 加载结果；接口测：缓存命中
- [x] 3.3 新增 `PermissionService`：权限 CRUD + 编码唯一校验 + 删除时校验被「操作」类型引用的子权限（拒绝） — 单测：编码重复、被引用删除分别抛对应错误码
- [x] 3.4 新增 `RoleService`：角色 CRUD + 编码唯一校验；`SUPER_ADMIN` 禁止删除/停用/改编码；删除时清理 `sys_user_role` 与 `sys_role_permission`；`updateRolePermissions` 覆盖更新角色权限集并失效相关用户缓存 — 单测：超管保护、清空权限、覆盖更新
- [x] 3.5 新增 `UserRoleService`：`assignUserRoles`（覆盖式）、`listUserRoles`；移除角色时校验不得移除「最后一名持有 `SUPER_ADMIN` 的用户」的该角色（957） — 单测：末个超管保护
- [x] 3.6 扩展 `AuthUserDetails` 为 `record(userId, username, roles, permissions)`，并调整所有 `details.userId()` 调用点兼容 — 单测/手测：编译通过、功能不变
- [x] 3.7 修改 `JwtAuthenticationFilter`：认证通过后调用 `loadAuthorities` 注入真实角色（`ROLE_<code>`）与权限（`<permCode>`）authorities — 接口测：普通用户仅 `ROLE_USER`，超管含全权限

## 4. 后端接入层：Controller 与鉴权

- [x] 4.1 `SecurityConfig` 增加 `@EnableMethodSecurity` — 接口测：`@PreAuthorize` 生效
- [x] 4.2 新增 `PermissionController`（`/api/v1/permissions`）：GET 列表/POST/PUT/DELETE，按 D6 加 `@PreAuthorize` — 接口测：对应 four 权限点
- [x] 4.3 新增 `RoleController`（`/api/v1/roles`）：GET/POST/PUT/DELETE + `GET/PUT {id}/permissions`，按 D6 加 `@PreAuthorize` — 接口测
- [x] 4.4 新增 `UserAdminController`（`/api/v1/users`）：GET 列表/POST 代建/PUT 编辑(含状态与重置密码)/DELETE 逻辑删除+清理角色 + `GET/PUT {id}/roles`，按 D6 加 `@PreAuthorize`；DO 全部转 VO 不暴露敏感字段；禁止删除当前登录用户自身 — 接口测：各权限点、路径与 `/users/me` 不冲突
- [x] 4.5 修改 `UserController./users/me`：返回 `roles` 与 `permissions`（无权限时返回空数组） — 接口测：`curl` 校验超管返回全权限、普通用户空数组

## 5. 前端 API 与状态

- [x] 5.1 `src/api/auth.ts` 的 `UserInfo` 增加 `roles: string[]`、`permissions: string[]` — 类型校验通过
- [x] 5.2 新增 `src/api/system.ts`：封装 permissions/roles/users 三组接口（分页、CRUD、分配角色、分配权限） — 手测：接口可达
- [x] 5.3 `src/stores/auth.ts` 在 `user` 中透出 `roles`/`permissions`，并补 `hasPerm`/`hasRole` getter — 手测：登录后 store 可见权限

## 6. 前端视图

- [x] 6.1 新增 `src/views/system/UserManageView.vue`：表格+搜索、新增、编辑、启停用、删除、「分配角色」弹窗（多选） — 手测：CRUD 与分配角色
- [x] 6.2 新增 `src/views/system/RoleManageView.vue`：表格、新增、编辑、删除、「分配权限」弹窗（按菜单/操作分组的权限树） — 手测：CRUD 与分配权限
- [x] 6.3 新增 `src/views/system/PermissionManageView.vue`：菜单/操作列表，新增/编辑/删除 — 手测：CRUD

## 7. 前端路由、守卫与菜单

- [x] 7.1 `src/router/index.ts` 新增 `system/*` 三个子路由（meta.perm 声明所需权限）与 `/403` 页 — 手测：路由可跳转
- [x] 7.2 `src/router/guards.ts` 在认证通过后校验 `meta.perm` 与 `authStore.hasPerm`，不命中则跳转 `/403` — 手测：普通用户直接访问被拦
- [x] 7.3 `src/layouts/DefaultLayout.vue` 新增「系统管理」`el-sub-menu` 分组，按 `hasPerm` 过滤渲染用户/角色/权限三个子项 — 手测：超管可见、普通用户不可见

## 8. 验证与文档

- [ ] 8.1 后端执行 Flyway 迁移成功，登录 `SUPER_ADMIN` 账号走全链路手测（users/me 全权限 → 三管理页 CRUD → 分配权限/角色） — 手测（需本地 MySQL/Redis，待环境就绪执行）
- [x] 8.2 新增基础单测覆盖 Service 层核心校验（编码重复、超管保护、末个超管保护、权限被引用删除） — `RoleServiceTest`/`PermissionServiceTest`/`UserRoleServiceTest` 通过 `mvnw test`
- [x] 8.3 更新 `docs/api/auth.md`（users/me 返回 roles/permissions），新增 `docs/api/system.md` 记录三组管理接口 — 文档评审
- [x] 8.4 前端执行 `npx vue-tsc --noEmit` 通过；后端 `mvnw compile` + 目标单测通过 — 构建校验