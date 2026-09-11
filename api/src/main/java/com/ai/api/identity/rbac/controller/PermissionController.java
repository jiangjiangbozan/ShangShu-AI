package com.ai.api.identity.rbac.controller;

import com.ai.api.common.result.Result;
import com.ai.api.identity.rbac.entity.SysPermission;
import com.ai.api.identity.rbac.service.PermissionService;
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
 * 权限资源管理 REST API。
 *
 * <p>依据 design.md D6：路径前缀 {@code /api/v1/permissions}，方法级鉴权。
 *
 * <p>DO 一律转为 {@link PermissionVO} 对外返回，避免直接暴露实体。
 *
 * @author add-rbac-management
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

  private final PermissionService permissionService;

  public PermissionController(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  /** 权限点 VO。 */
  @Data
  public static class PermissionVO {
    private Long permissionId;
    private Long parentId;
    private String code;
    private String name;
    private Integer type;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
  }

  /**
   * 查询权限点列表。
   *
   * <p>GET /api/v1/permissions?type=1
   */
  @GetMapping
  @PreAuthorize("hasAuthority('system:permission:menu')")
  public Result<List<PermissionVO>> list(@RequestParam(required = false) Integer type) {
    List<SysPermission> list = permissionService.list(type);
    return Result.success(list.stream().map(PermissionController::toVO).toList());
  }

  /**
   * 新增权限点。
   *
   * <p>POST /api/v1/permissions
   */
  @PostMapping
  @PreAuthorize("hasAuthority('system:permission:create')")
  public Result<PermissionVO> create(@RequestBody SysPermission permission) {
    SysPermission created = permissionService.create(permission);
    return Result.success(toVO(created));
  }

  /**
   * 更新权限点。
   *
   * <p>PUT /api/v1/permissions/{id}
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('system:permission:update')")
  public Result<PermissionVO> update(@PathVariable Long id, @RequestBody SysPermission permission) {
    SysPermission updated = permissionService.update(id, permission);
    return Result.success(toVO(updated));
  }

  /**
   * 删除权限点。
   *
   * <p>DELETE /api/v1/permissions/{id}
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('system:permission:delete')")
  public Result<Void> delete(@PathVariable Long id) {
    permissionService.delete(id);
    return Result.success(null);
  }

  private static PermissionVO toVO(SysPermission p) {
    PermissionVO vo = new PermissionVO();
    vo.setPermissionId(p.getId());
    vo.setParentId(p.getParentId());
    vo.setCode(p.getCode());
    vo.setName(p.getName());
    vo.setType(p.getType());
    vo.setPath(p.getPath());
    vo.setIcon(p.getIcon());
    vo.setSort(p.getSort());
    vo.setStatus(p.getStatus());
    return vo;
  }
}
