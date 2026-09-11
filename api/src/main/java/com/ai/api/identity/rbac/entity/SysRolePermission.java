package com.ai.api.identity.rbac.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色-权限关联实体（对应 {@code sys_role_permission} 表）
 *
 * <p>依据 design.md D1：一个角色可绑定多个权限点，多对多通过本中间表承载。
 *
 * @author add-rbac-management
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_permission")
public class SysRolePermission extends BaseDO {

  private static final long serialVersionUID = 1L;

  /** 角色 ID（sys_role.id） */
  private Long roleId;

  /** 权限 ID（sys_permission.id） */
  private Long permissionId;
}
