package com.ai.api.identity.rbac.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户-角色关联实体（对应 {@code sys_user_role} 表）
 *
 * <p>依据 design.md D1：一个用户可绑定多个角色，多对多通过本中间表承载。
 *
 * @author add-rbac-management
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_role")
public class SysUserRole extends BaseDO {

  private static final long serialVersionUID = 1L;

  /** 用户 ID（sys_user.id） */
  private Long userId;

  /** 角色 ID（sys_role.id） */
  private Long roleId;
}
