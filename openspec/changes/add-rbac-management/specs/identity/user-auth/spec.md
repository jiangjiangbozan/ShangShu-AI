## ADDED Requirements

### Requirement: 认证上下文携带角色与权限
系统 SHALL 在鉴权阶段为每个已认证用户加载其全部角色与权限点，并写入认证上下文供接口鉴权使用。仅凭已验证身份（`ROLE_USER`）不再足以通过需要权限的接口，接口鉴权 MUST 依据用户实际拥有的权限点进行判断。

#### Scenario: 已认证但无所需权限
- **WHEN** 一个已通过 JWT 认证的普通用户请求一个需要特定权限点的接口
- **THEN** 系统依据其权限点集合判定无权限，并返回无权限错误

#### Scenario: 认证上下文含全部权限
- **WHEN** 一个用户的 JWT 校验通过，其角色与权限被成功加载
- **THEN** 该请求的鉴权针对其完整权限集合进行，而非固定的单一 `ROLE_USER`

### Requirement: 当前用户信息返回角色与权限
系统 SHALL 在 `GET /api/v1/users/me` 的返回内容中新增 `roles`（角色编码列表）与 `permissions`（权限点编码列表）字段，供前端进行菜单显隐与路由守卫判断。该接口 MUST 在用户无角色/权限时返回空数组而非缺省错误。

#### Scenario: 超管查看当前用户信息
- **WHEN** 持有 `SUPER_ADMIN` 角色的用户调用 `GET /api/v1/users/me`
- **THEN** 返回的 `roles` 包含 `SUPER_ADMIN`，`permissions` 包含全部权限点编码

#### Scenario: 无角色用户查看当前用户信息
- **WHEN** 一个未被分配任何角色的用户调用 `GET /api/v1/users/me`
- **THEN** 返回的 `roles` 与 `permissions` 均为空数组，接口正常成功