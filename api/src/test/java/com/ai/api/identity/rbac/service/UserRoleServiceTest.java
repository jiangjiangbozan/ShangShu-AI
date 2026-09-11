package com.ai.api.identity.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.identity.rbac.entity.SysRole;
import com.ai.api.identity.rbac.entity.SysUserRole;
import com.ai.api.identity.rbac.mapper.SysRoleMapper;
import com.ai.api.identity.rbac.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link UserRoleService} 单元测试（add-rbac-management）。
 *
 * <p>覆盖核心校验：移除唯一剩余 SUPER_ADMIN 角色的「最后一名管理员」保护。
 *
 * @author add-rbac-management
 */
@DisplayName("UserRoleService 单元测试")
class UserRoleServiceTest {

  private SysUserRoleMapper userRoleMapper;
  private SysRoleMapper roleMapper;
  private RbacPermissionService rbacPermissionService;
  private UserRoleService userRoleService;

  @BeforeEach
  void setup() {
    userRoleMapper = mock(SysUserRoleMapper.class);
    roleMapper = mock(SysRoleMapper.class);
    rbacPermissionService = mock(RbacPermissionService.class);
    userRoleService = new UserRoleService(userRoleMapper, roleMapper, rbacPermissionService);
  }

  @Test
  @DisplayName("移除最后一名超级管理员角色：抛 RBAC_LAST_SUPER_ROLE(957)")
  void assignUserRoles_removingLastSuperAdmin_shouldThrow() {
    // 当前用户持有 SUPER_ADMIN 角色（roleId=1）
    SysUserRole relation = new SysUserRole();
    relation.setUserId(1L);
    relation.setRoleId(1L);
    when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation));

    SysRole superRole = new SysRole();
    superRole.setId(1L);
    superRole.setCode(SysRole.CODE_SUPER_ADMIN);
    when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(superRole);

    // 无其他用户持有 SUPER_ADMIN
    when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

    // 将角色覆盖为空集合（即移除），应触发保护
    assertThatThrownBy(() -> userRoleService.assignUserRoles(1L, List.of()))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(957));
  }
}
