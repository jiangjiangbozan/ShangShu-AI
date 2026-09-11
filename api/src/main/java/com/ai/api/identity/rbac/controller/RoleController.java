package com.ai.api.identity.rbac.controller;

import com.ai.api.common.result.Result;
import com.ai.api.identity.rbac.entity.SysPermission;
import com.ai.api.identity.rbac.entity.SysRole;
import com.ai.api.identity.rbac.service.RoleService;
import java.util.List;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理 REST API。
 *
 * <p>依据 design.md D6：路径前缀 {@code /api/v1/roles}，方法级鉴权。
 *
 * <p>DO 一律转为 {@link RoleVO} / {@link PermissionIdListReq} 对外交互。
 *
 * @author add-rbac-management
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  /** 角色 VO。 */
  @Data
  public static class RoleVO {
    private Long roleId;
    private String code;
    private String name;
    private String remark;
    private Integer status;
  }

  /** 覆盖式更新角色权限集请求（权限点 ID 集合）。 */
  @Data
  public static class PermissionIdListReq {
    private List<Long> permissionIds;
  }

  /**
   * 查询角色列表。
   *
   * <p>GET /api/v1/roles?keyword=xxx
   */
  @GetMapping
  @PreAuthorize("hasAuthority('system:role:menu')")
  public Result<List<RoleVO>> list(@RequestParam(required = false) String keyword) {
    List<SysRole> list = roleService.list(keyword);
    return Result.success(list.stream().map(RoleController::toVO).toList());
  }

  /**
   * 新增角色。
   *
   * <p>POST /api/v1/roles
   */
  @PostMapping
  @PreAuthorize("hasAuthority('system:role:create')")
  public Result<RoleVO> create(@RequestBody SysRole role) {
    SysRole created = roleService.create(role);
    return Result.success(toVO(created));
  }

  /**
   * 更新角色。
   *
   * <p>PUT /api/v1/roles/{id}
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('system:role:update')")
  public Result<RoleVO> update(@PathVariable Long id, @RequestBody SysRole role) {
    SysRole updated = roleService.update(id, role);
    return Result.success(toVO(updated));
  }

  /**
   * 删除角色。
   *
   * <p>DELETE /api/v1/roles/{id}
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('system:role:delete')")
  public Result<Void> delete(@PathVariable Long id) {
    roleService.delete(id);
    return Result.success(null);
  }

  /**
   * 查询角色已绑定权限点。
   *
   * <p>GET /api/v1/roles/{id}/permissions
   */
  @GetMapping("/{id}/permissions")
  @PreAuthorize("hasAuthority('system:role:menu')")
  public Result<List<Long>> rolePermissions(@PathVariable Long id) {
    List<SysPermission> perms = roleService.listRolePermissions(id);
    return Result.success(perms.stream().map(SysPermission::getId).toList());
  }

  /**
   * 覆盖式更新角色权限集（分配权限）。
   *
   * <p>PUT /api/v1/roles/{id}/permissions
   */
  @PutMapping("/{id}/permissions")
  @PreAuthorize("hasAuthority('system:role:assign')")
  public Result<Void> assignPermissions(
      @PathVariable Long id, @RequestBody PermissionIdListReq req) {
    roleService.updateRolePermissions(id, req == null ? null : req.getPermissionIds());
    return Result.success(null);
  }

  private static RoleVO toVO(SysRole r) {
    RoleVO vo = new RoleVO();
    vo.setRoleId(r.getId());
    vo.setCode(r.getCode());
    vo.setName(r.getName());
    vo.setRemark(r.getRemark());
    vo.setStatus(r.getStatus());
    return vo;
  }
}
