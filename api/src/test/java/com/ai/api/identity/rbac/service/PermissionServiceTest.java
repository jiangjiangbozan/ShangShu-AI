package com.ai.api.identity.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.identity.rbac.entity.SysPermission;
import com.ai.api.identity.rbac.mapper.SysPermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PermissionService} 单元测试（add-rbac-management）。
 *
 * <p>覆盖核心校验：编码重复、权限不存在、被「操作」子权限引用删除被拒。
 *
 * @author add-rbac-management
 */
@DisplayName("PermissionService 单元测试")
class PermissionServiceTest {

  private SysPermissionMapper permissionMapper;
  private RbacPermissionService rbacPermissionService;
  private PermissionService permissionService;

  @BeforeEach
  void setup() {
    permissionMapper = mock(SysPermissionMapper.class);
    rbacPermissionService = mock(RbacPermissionService.class);
    permissionService = new PermissionService(permissionMapper, rbacPermissionService);
  }

  @Test
  @DisplayName("新建编码重复的权限点：抛 RBAC_PERMISSION_CODE_DUPLICATE(953)")
  void create_withDuplicateCode_shouldThrow() {
    SysPermission permission = new SysPermission();
    permission.setCode("system:user:menu");
    when(permissionMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

    assertThatThrownBy(() -> permissionService.create(permission))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(953));
  }

  @Test
  @DisplayName("删除不存在的权限点：抛 RBAC_PERMISSION_NOT_FOUND(952)")
  void delete_notFound_shouldThrow() {
    when(permissionMapper.selectById(99L)).thenReturn(null);

    assertThatThrownBy(() -> permissionService.delete(99L))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(952));
  }

  @Test
  @DisplayName("删除仍被「操作」子权限引用的菜单权限：抛 RBAC_PERMISSION_IN_USE(954)")
  void delete_withChildren_shouldThrow() {
    SysPermission existing = new SysPermission();
    existing.setId(1L);
    when(permissionMapper.selectById(1L)).thenReturn(existing);
    when(permissionMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

    assertThatThrownBy(() -> permissionService.delete(1L))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(954));
  }
}
