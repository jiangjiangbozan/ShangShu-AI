package com.ai.api.identity.rbac.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.identity.rbac.entity.SysPermission;
import com.ai.api.identity.rbac.entity.SysRole;
import com.ai.api.identity.rbac.entity.SysRolePermission;
import com.ai.api.identity.rbac.entity.SysUserRole;
import com.ai.api.identity.rbac.mapper.SysPermissionMapper;
import com.ai.api.identity.rbac.mapper.SysRoleMapper;
import com.ai.api.identity.rbac.mapper.SysRolePermissionMapper;
import com.ai.api.identity.rbac.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 角色服务
 *
 * <p>依据 design.md D、spec "角色管理 / 超级管理员角色 / 角色权限配置"：
 *
 * <ul>
 *   <li>角色 CRUD，角色编码唯一；
 *   <li>{@code SUPER_ADMIN} 禁止删除/停用/改编码，且权限集不可被覆盖缩减；
 *   <li>分配权限为覆盖式更新，updateRolePermissions 对超级角色直接拒绝。
 * </ul>
 *
 * @author add-rbac-management
 */
@Service
public class RoleService {

  private final SysRoleMapper roleMapper;
  private final SysRolePermissionMapper rolePermissionMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final SysPermissionMapper permissionMapper;
  private final RbacPermissionService rbacPermissionService;

  public RoleService(
      SysRoleMapper roleMapper,
      SysRolePermissionMapper rolePermissionMapper,
      SysUserRoleMapper userRoleMapper,
      SysPermissionMapper permissionMapper,
      RbacPermissionService rbacPermissionService) {
    this.roleMapper = roleMapper;
    this.rolePermissionMapper = rolePermissionMapper;
    this.userRoleMapper = userRoleMapper;
    this.permissionMapper = permissionMapper;
    this.rbacPermissionService = rbacPermissionService;
  }

  /**
   * 查询角色列表。
   *
   * @param keyword 角色编码/名称模糊关键字（可空）
   * @return 角色列表（DO，Controller 层转 VO）
   */
  public List<SysRole> list(String keyword) {
    LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      wrapper.and(w -> w.like(SysRole::getCode, keyword).or().like(SysRole::getName, keyword));
    }
    wrapper.orderByAsc(SysRole::getId);
    return roleMapper.selectList(wrapper);
  }

  /**
   * 新增角色。
   *
   * <p>禁止创建 `SUPER_ADMIN`，角色编码唯一。
   *
   * @param role 角色（含 code/name/remark/status）
   * @return 已创建角色
   */
  @Transactional(rollbackFor = Exception.class)
  public SysRole create(SysRole role) {
    if (SysRole.CODE_SUPER_ADMIN.equalsIgnoreCase(role.getCode())) {
      throw new BusinessException(ErrorCode.RBAC_SUPER_ROLE_PROTECTED);
    }
    checkCodeUnique(role.getCode(), null);
    roleMapper.insert(role);
    return role;
  }

  /**
   * 更新角色。
   *
   * <p>超级管理员角色禁止修改编码、禁止停用。
   *
   * @param id 角色 ID
   * @param update 待更新字段
   * @return 更新后角色
   */
  @Transactional(rollbackFor = Exception.class)
  public SysRole update(Long id, SysRole update) {
    SysRole existing = getById(id);
    boolean isSuper = existing.isSuperAdmin();
    if (isSuper) {
      throw new BusinessException(ErrorCode.RBAC_SUPER_ROLE_PROTECTED);
    }
    if (StringUtils.hasText(update.getCode())) {
      checkCodeUnique(update.getCode(), id);
      existing.setCode(update.getCode());
    }
    if (update.getName() != null) existing.setName(update.getName());
    if (update.getRemark() != null) existing.setRemark(update.getRemark());
    if (update.getStatus() != null) existing.setStatus(update.getStatus());
    roleMapper.updateById(existing);
    return existing;
  }

  /**
   * 删除角色并清理其用户绑定与权限绑定。
   *
   * @param id 角色 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void delete(Long id) {
    SysRole existing = getById(id);
    if (existing.isSuperAdmin()) {
      throw new BusinessException(ErrorCode.RBAC_SUPER_ROLE_PROTECTED);
    }
    // 解除用户绑定
    userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
    // 解除权限绑定
    rolePermissionMapper.delete(
        new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
    roleMapper.deleteById(id);
    rbacPermissionService.evictAll();
  }

  /**
   * 查询角色已绑定的权限点列表。
   *
   * @param roleId 角色 ID
   * @return 权限点列表
   */
  public List<SysPermission> listRolePermissions(Long roleId) {
    getById(roleId);
    List<Long> permissionIds =
        rolePermissionMapper
            .selectList(
                new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, roleId))
            .stream()
            .map(SysRolePermission::getPermissionId)
            .distinct()
            .toList();
    if (permissionIds.isEmpty()) {
      return List.of();
    }
    return permissionMapper.selectBatchIds(permissionIds);
  }

  /**
   * 覆盖式更新角色权限集。
   *
   * <p>超级管理员角色的权限集受保护，任何覆盖尝试均被拒绝（其权限由 loadAuthorities 动态全量授予）。
   *
   * @param roleId 角色 ID
   * @param permissionIds 目标权限点 ID 集合
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
    SysRole existing = getById(roleId);
    if (existing.isSuperAdmin()) {
      throw new BusinessException(ErrorCode.RBAC_SUPER_ROLE_PROTECTED);
    }
    // 覆盖：先软删旧绑定，再插入新绑定
    rolePermissionMapper.delete(
        new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
    if (permissionIds != null) {
      List<Long> distinct = permissionIds.stream().distinct().toList();
      for (Long permissionId : distinct) {
        SysRolePermission rp = new SysRolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        rolePermissionMapper.insert(rp);
      }
    }
    // 失效持有该角色的所有用户的权限缓存
    List<Long> affectedUserIds =
        userRoleMapper
            .selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
            .stream()
            .map(SysUserRole::getUserId)
            .distinct()
            .toList();
    rbacPermissionService.evictUsers(affectedUserIds);
  }

  private SysRole getById(Long id) {
    SysRole role = roleMapper.selectById(id);
    if (role == null) {
      throw new BusinessException(ErrorCode.RBAC_ROLE_NOT_FOUND);
    }
    return role;
  }

  private void checkCodeUnique(String code, Long excludeId) {
    LambdaQueryWrapper<SysRole> wrapper =
        new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code);
    if (excludeId != null) {
      wrapper.ne(SysRole::getId, excludeId);
    }
    if (roleMapper.selectCount(wrapper) > 0) {
      throw new BusinessException(ErrorCode.RBAC_ROLE_CODE_DUPLICATE);
    }
  }
}
