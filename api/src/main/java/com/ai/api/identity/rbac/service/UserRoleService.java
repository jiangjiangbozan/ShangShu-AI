package com.ai.api.identity.rbac.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.identity.rbac.entity.SysRole;
import com.ai.api.identity.rbac.entity.SysUserRole;
import com.ai.api.identity.rbac.mapper.SysRoleMapper;
import com.ai.api.identity.rbac.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户角色分配服务
 *
 * <p>依据 design.md D5、spec "用户角色分配"：
 *
 * <ul>
 *   <li>覆盖式绑定用户角色；
 *   <li>禁止移除最后一名仍在使用的超级管理员的 `SUPER_ADMIN` 角色，保证系统始终保留至少一位管理员。
 * </ul>
 *
 * @author add-rbac-management
 */
@Service
public class UserRoleService {

  private final SysUserRoleMapper userRoleMapper;
  private final SysRoleMapper roleMapper;
  private final RbacPermissionService rbacPermissionService;

  public UserRoleService(
      SysUserRoleMapper userRoleMapper,
      SysRoleMapper roleMapper,
      RbacPermissionService rbacPermissionService) {
    this.userRoleMapper = userRoleMapper;
    this.roleMapper = roleMapper;
    this.rbacPermissionService = rbacPermissionService;
  }

  /**
   * 查询用户当前绑定的角色列表。
   *
   * @param userId 用户 ID
   * @return 角色列表
   */
  public List<SysRole> listUserRoles(Long userId) {
    List<Long> roleIds =
        userRoleMapper
            .selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
            .stream()
            .map(SysUserRole::getRoleId)
            .distinct()
            .toList();
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return roleMapper.selectBatchIds(roleIds);
  }

  /**
   * 覆盖式分配用户角色。
   *
   * <p>若移除的某角色为 `SUPER_ADMIN` 且系统中仅剩当前用户持有，则拒绝，避免无主。
   *
   * @param userId 用户 ID
   * @param roleIds 目标角色 ID 集合（null/空表示清空角色）
   */
  @Transactional(rollbackFor = Exception.class)
  public void assignUserRoles(Long userId, List<Long> roleIds) {
    List<SysUserRole> current =
        userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
    Set<Long> currentRoleIds =
        current.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());

    Set<Long> targetRoleIds =
        roleIds == null
            ? Set.of()
            : roleIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());

    // 校验被移除的超级角色是否触发「最后一名管理员」保护
    Set<Long> removed = new HashSet<>(currentRoleIds);
    removed.removeAll(targetRoleIds);
    if (!removed.isEmpty()) {
      Long superRoleId = findSuperRoleId();
      if (superRoleId != null && removed.contains(superRoleId)) {
        Long otherSuperCount =
            userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getRoleId, superRoleId)
                    .ne(SysUserRole::getUserId, userId));
        if (otherSuperCount == null || otherSuperCount == 0) {
          throw new BusinessException(ErrorCode.RBAC_LAST_SUPER_ROLE);
        }
      }
    }

    // 覆盖式更新：先软删旧绑定，再插入新绑定
    userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
    if (!targetRoleIds.isEmpty()) {
      List<SysRole> existingRoles = roleMapper.selectBatchIds(targetRoleIds);
      Set<Long> validRoleIds =
          existingRoles.stream().map(SysRole::getId).collect(Collectors.toSet());
      for (Long roleId : targetRoleIds) {
        if (validRoleIds.contains(roleId)) {
          SysUserRole relation = new SysUserRole();
          relation.setUserId(userId);
          relation.setRoleId(roleId);
          userRoleMapper.insert(relation);
        }
      }
    }
    // 用户角色变更影响鉴权结果，失效该用户权限缓存
    rbacPermissionService.evictUsers(List.of(userId));
  }

  private Long findSuperRoleId() {
    SysRole superRole =
        roleMapper.selectOne(
            new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, SysRole.CODE_SUPER_ADMIN)
                .last("limit 1"));
    return superRole == null ? null : superRole.getId();
  }
}
