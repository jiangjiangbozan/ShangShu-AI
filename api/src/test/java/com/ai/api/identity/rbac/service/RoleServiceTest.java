package com.ai.api.identity.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.identity.rbac.entity.SysRole;
import com.ai.api.identity.rbac.mapper.SysPermissionMapper;
import com.ai.api.identity.rbac.mapper.SysRoleMapper;
import com.ai.api.identity.rbac.mapper.SysRolePermissionMapper;
import com.ai.api.identity.rbac.mapper.SysUserRoleMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RoleService} 单元测试（add-rbac-management）。
 *
 * <p>覆盖核心校验：新建公开角色编码重复、SUPER_ADMIN 受保护（新增/编辑/删除/分配权限）。
 *
 * @author add-rbac-management
 */
@DisplayName("RoleService 单元测试")
class RoleServiceTest {

  private SysRoleMapper roleMapper;
  private SysRolePermissionMapper rolePermissionMapper;
  private SysUserRoleMapper userRoleMapper;
  private SysPermissionMapper permissionMapper;
  private RbacPermissionService rbacPermissionService;
  private RoleService roleService;

  @BeforeEach
  void setup() {
    roleMapper = mock(SysRoleMapper.class);
    rolePermissionMapper = mock(SysRolePermissionMapper.class);
    userRoleMapper = mock(SysUserRoleMapper.class);
    permissionMapper = mock(SysPermissionMapper.class);
    rbacPermissionService = mock(RbacPermissionService.class);
    roleService =
        new RoleService(
            roleMapper,
            rolePermissionMapper,
            userRoleMapper,
            permissionMapper,
            rbacPermissionService);
  }

  @Test
  @DisplayName("新建编码重复的角色：抛 RBAC_ROLE_CODE_DUPLICATE(951)")
  void create_withDuplicateCode_shouldThrow() {
    SysRole role = new SysRole();
    role.setCode("USER_ADMIN");
    when(roleMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

    assertThatThrownBy(() -> roleService.create(role))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(951));
  }

  @Test
  @DisplayName("新建 SUPER_ADMIN 角色：抛 RBAC_SUPER_ROLE_PROTECTED(956)")
  void create_superAdmin_shouldThrow() {
    SysRole role = new SysRole();
    role.setCode(SysRole.CODE_SUPER_ADMIN);

    assertThatThrownBy(() -> roleService.create(role))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(956));
  }

  @Test
  @DisplayName("编辑 SUPER_ADMIN 角色：抛 RBAC_SUPER_ROLE_PROTECTED(956)")
  void update_superAdmin_shouldThrow() {
    SysRole existing = new SysRole();
    existing.setId(1L);
    existing.setCode(SysRole.CODE_SUPER_ADMIN);
    when(roleMapper.selectById(1L)).thenReturn(existing);

    assertThatThrownBy(() -> roleService.update(1L, new SysRole()))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(956));
  }

  @Test
  @DisplayName("删除 SUPER_ADMIN 角色：抛 RBAC_SUPER_ROLE_PROTECTED(956)")
  void delete_superAdmin_shouldThrow() {
    SysRole existing = new SysRole();
    existing.setId(1L);
    existing.setCode(SysRole.CODE_SUPER_ADMIN);
    when(roleMapper.selectById(1L)).thenReturn(existing);

    assertThatThrownBy(() -> roleService.delete(1L))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(956));
  }

  @Test
  @DisplayName("为 SUPER_ADMIN 覆盖分配权限：抛 RBAC_SUPER_ROLE_PROTECTED(956)")
  void updateRolePermissions_superAdmin_shouldThrow() {
    SysRole existing = new SysRole();
    existing.setId(1L);
    existing.setCode(SysRole.CODE_SUPER_ADMIN);
    when(roleMapper.selectById(1L)).thenReturn(existing);

    assertThatThrownBy(() -> roleService.updateRolePermissions(1L, List.of(10L, 11L)))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(956));
  }
}
