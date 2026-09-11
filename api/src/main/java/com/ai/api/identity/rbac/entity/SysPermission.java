package com.ai.api.identity.rbac.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限/资源实体（对应 {@code sys_permission} 表）
 *
 * <p>依据 design.md D1：定义系统中的菜单资源与操作权限点，供角色绑定与接口方法级鉴权使用。
 *
 * <p>类型见 {@link #TYPE_MENU} 与 {@link #TYPE_OPERATION}。
 *
 * @author add-rbac-management
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseDO {

  private static final long serialVersionUID = 1L;

  /** 权限类型：菜单 */
  public static final int TYPE_MENU = 1;

  /** 权限类型：操作 */
  public static final int TYPE_OPERATION = 2;

  /** 父级菜单 ID（0=根） */
  private Long parentId;

  /** 权限编码，如 system:user:create（全局唯一） */
  private String code;

  /** 权限名称 */
  private String name;

  /** 权限类型：1=菜单, 2=操作 */
  private Integer type;

  /** 菜单路由路径（type=菜单 时使用） */
  private String path;

  /** 菜单图标 */
  private String icon;

  /** 排序权重 */
  private Integer sort;

  /** 状态：1=启用, 0=禁用 */
  private Integer status;
}
