package com.ai.api.identity.rbac.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.identity.rbac.entity.SysPermission;
import com.ai.api.identity.rbac.mapper.SysPermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限资源服务
 *
 * <p>依据 design.md D、spec "权限资源管理"：权限点 CRUD、编码唯一校验、删除时校验「操作」子权限引用。
 *
 * <p>权限点变更会改变鉴权结果，故任何变更后统一失效全部用户权限缓存。
 *
 * @author add-rbac-management
 */
@Service
public class PermissionService {

  private final SysPermissionMapper permissionMapper;
  private final RbacPermissionService rbacPermissionService;

  public PermissionService(
      SysPermissionMapper permissionMapper, RbacPermissionService rbacPermissionService) {
    this.permissionMapper = permissionMapper;
    this.rbacPermissionService = rbacPermissionService;
  }

  /**
   * 查询权限点列表。
   *
   * @param type 权限类型（可空，null 或 0 表示全部）
   * @return 权限点列表（DO，Controller 层转 VO）
   */
  public List<SysPermission> list(Integer type) {
    LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
    if (type != null && (type == SysPermission.TYPE_MENU || type == SysPermission.TYPE_OPERATION)) {
      wrapper.eq(SysPermission::getType, type);
    }
    wrapper.orderByAsc(SysPermission::getSort).orderByAsc(SysPermission::getId);
    return permissionMapper.selectList(wrapper);
  }

  /**
   * 新增权限点。
   *
   * <p>校验编码唯一。
   *
   * @param permission 权限点（含 code/name/type 等）
   * @return 已创建权限点
   */
  @Transactional(rollbackFor = Exception.class)
  public SysPermission create(SysPermission permission) {
    checkCodeUnique(permission.getCode(), null);
    permissionMapper.insert(permission);
    rbacPermissionService.evictAll();
    return permission;
  }

  /**
   * 更新权限点。
   *
   * <p>校验编码唯一（排除自身）。
   *
   * @param id 权限点 ID
   * @param update 待更新字段
   * @return 更新后的权限点
   */
  @Transactional(rollbackFor = Exception.class)
  public SysPermission update(Long id, SysPermission update) {
    SysPermission existing = getById(id);
    if (update.getCode() != null && !existing.getCode().equals(update.getCode())) {
      checkCodeUnique(update.getCode(), id);
      existing.setCode(update.getCode());
    }
    if (update.getName() != null) existing.setName(update.getName());
    if (update.getType() != null) existing.setType(update.getType());
    if (update.getParentId() != null) existing.setParentId(update.getParentId());
    if (update.getPath() != null) existing.setPath(update.getPath());
    if (update.getIcon() != null) existing.setIcon(update.getIcon());
    if (update.getSort() != null) existing.setSort(update.getSort());
    if (update.getStatus() != null) existing.setStatus(update.getStatus());
    permissionMapper.updateById(existing);
    rbacPermissionService.evictAll();
    return existing;
  }

  /**
   * 删除权限点。
   *
   * <p>校验是否仍被「操作」子权限引用（以其为父级），是则拒绝删除。
   *
   * @param id 权限点 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void delete(Long id) {
    SysPermission existing = getById(id);
    Long childCount =
        permissionMapper.selectCount(
            new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, existing.getId()));
    if (childCount != null && childCount > 0) {
      throw new BusinessException(ErrorCode.RBAC_PERMISSION_IN_USE);
    }
    permissionMapper.deleteById(id);
    rbacPermissionService.evictAll();
  }

  private SysPermission getById(Long id) {
    SysPermission permission = permissionMapper.selectById(id);
    if (permission == null) {
      throw new BusinessException(ErrorCode.RBAC_PERMISSION_NOT_FOUND);
    }
    return permission;
  }

  private void checkCodeUnique(String code, Long excludeId) {
    if (code == null || code.isBlank()) {
      throw new BusinessException(ErrorCode.RBAC_PERMISSION_NOT_FOUND, "权限编码不能为空");
    }
    LambdaQueryWrapper<SysPermission> wrapper =
        new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getCode, code);
    if (excludeId != null) {
      wrapper.ne(SysPermission::getId, excludeId);
    }
    if (permissionMapper.selectCount(wrapper) > 0) {
      throw new BusinessException(ErrorCode.RBAC_PERMISSION_CODE_DUPLICATE);
    }
  }
}
