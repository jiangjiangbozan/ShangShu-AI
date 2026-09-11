package com.ai.api.identity.rbac.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体（对应 {@code sys_role} 表）
 *
 * <p>依据 design.md D1：角色是权限承载单元，可绑定多个权限并分配给多个用户。
 *
 * <p>内置固定角色编码见 {@link #CODE_SUPER_ADMIN}（受保护，禁止删除/停用/改编码）。
 *
 * @author add-rbac-management
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseDO {

  private static final long serialVersionUID = 1L;

  /** 内置超级管理员角色编码（受保护） */
  public static final String CODE_SUPER_ADMIN = "SUPER_ADMIN";

  /** 角色编码（全局唯一） */
  private String code;

  /** 角色名称 */
  private String name;

  /** 备注 */
  private String remark;

  /** 状态：1=启用, 0=禁用 */
  private Integer status;

  /** 是否内置超级管理员角色 */
  public boolean isSuperAdmin() {
    return CODE_SUPER_ADMIN.equals(code);
  }
}
