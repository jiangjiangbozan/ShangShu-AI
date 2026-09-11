package com.ai.api.identity.rbac.controller;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.identity.entity.SystemUser;
import com.ai.api.identity.mapper.SystemUserMapper;
import com.ai.api.identity.rbac.entity.SysRole;
import com.ai.api.identity.rbac.service.UserRoleService;
import com.ai.api.identity.security.AuthUserDetails;
import com.ai.api.identity.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
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
 * 系统用户管理 REST API。
 *
 * <p>依据 design.md D6：路径前缀 {@code /api/v1/users}，与既有 {@code GET /users/me} 精确路径共存，
 * 针对用户管理场景（列表/代建/编辑/删除/分配角色）做方法级鉴权。
 *
 * <p>DO 一律转为 {@link UserVO} 对外返回，不暴露 password_hash 等敏感字段。
 *
 * @author add-rbac-management
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

  private final SystemUserMapper systemUserMapper;
  private final UserService userService;
  private final UserRoleService userRoleService;

  public UserAdminController(
      SystemUserMapper systemUserMapper, UserService userService, UserRoleService userRoleService) {
    this.systemUserMapper = systemUserMapper;
    this.userService = userService;
    this.userRoleService = userRoleService;
  }

  /** 用户 VO（不含敏感字段）。 */
  @Data
  public static class UserVO {
    private Long userId;
    private String username;
    private String email;
    private Integer status;
    private java.time.LocalDateTime lastLoginAt;
  }

  /** 代建用户请求。 */
  @Data
  public static class CreateUserReq {
    private String username;
    private String email;
    private String password;
    private List<Long> roleIds;
  }

  /** 编辑用户请求（可选字段：只更新非 null）。 */
  @Data
  public static class UpdateUserReq {
    private String username;
    private String email;
    private Integer status;
    private String password;
  }

  /** 分配角色请求。 */
  @Data
  public static class AssignRoleReq {
    private List<Long> roleIds;
  }

  /**
   * 分页查询用户列表。
   *
   * <p>GET /api/v1/users?page=1&size=20&keyword=xxx
   */
  @GetMapping
  @PreAuthorize("hasAuthority('system:user:menu')")
  public Result<PageResult<UserVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    LambdaQueryWrapper<SystemUser> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      wrapper.and(
          w -> w.like(SystemUser::getUsername, keyword).or().like(SystemUser::getEmail, keyword));
    }
    wrapper.orderByDesc(SystemUser::getId);
    Page<SystemUser> result = systemUserMapper.selectPage(new Page<>(page, size), wrapper);
    return Result.success(
        PageResult.of(
            result.getRecords().stream().map(UserAdminController::toVO).toList(),
            result.getTotal()));
  }

  /**
   * 管理员代建用户。
   *
   * <p>POST /api/v1/users
   */
  @PostMapping
  @PreAuthorize("hasAuthority('system:user:create')")
  public Result<UserVO> create(@RequestBody CreateUserReq req) {
    if (req == null
        || !StringUtils.hasText(req.getUsername())
        || !StringUtils.hasText(req.getEmail())
        || !StringUtils.hasText(req.getPassword())) {
      throw new BusinessException(ErrorCode.COMMON_BAD_PARAM);
    }
    SystemUser user = userService.register(req.getUsername(), req.getEmail(), req.getPassword());
    if (req.getRoleIds() != null && !req.getRoleIds().isEmpty()) {
      userRoleService.assignUserRoles(user.getId(), req.getRoleIds());
    }
    return Result.success(toVO(user));
  }

  /**
   * 编辑用户（资料/状态/重置密码）。
   *
   * <p>PUT /api/v1/users/{id}
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('system:user:update')")
  public Result<UserVO> update(@PathVariable Long id, @RequestBody UpdateUserReq req) {
    SystemUser user = userService.getById(id);
    if (req == null) {
      return Result.success(toVO(user));
    }
    if (StringUtils.hasText(req.getUsername())) {
      user.setUsername(req.getUsername());
    }
    if (StringUtils.hasText(req.getEmail())) {
      user.setEmail(req.getEmail());
    }
    if (req.getStatus() != null) {
      user.setStatus(req.getStatus());
    }
    systemUserMapper.updateById(user);
    if (StringUtils.hasText(req.getPassword())) {
      userService.updatePassword(id, req.getPassword());
    }
    return Result.success(toVO(userService.getById(id)));
  }

  /**
   * 删除用户（逻辑删除 + 清理角色）。
   *
   * <p>禁止删除当前登录用户自身；若该用户是最后一名超级管理员，由 {@link UserRoleService} 拒绝。
   *
   * <p>DELETE /api/v1/users/{id}
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('system:user:delete')")
  public Result<Void> delete(@PathVariable Long id) {
    Long currentUserId = currentUserId();
    if (currentUserId != null && currentUserId.equals(id)) {
      throw new BusinessException(ErrorCode.RBAC_SUPER_ROLE_PROTECTED, "不能删除当前登录用户");
    }
    // 清理角色（内部含「最后一名超管」保护）
    userRoleService.assignUserRoles(id, List.of());
    systemUserMapper.deleteById(id);
    return Result.success(null);
  }

  /**
   * 查询用户已绑定角色。
   *
   * <p>GET /api/v1/users/{id}/roles
   */
  @GetMapping("/{id}/roles")
  @PreAuthorize("hasAuthority('system:user:menu')")
  public Result<List<SysRole>> userRoles(@PathVariable Long id) {
    return Result.success(userRoleService.listUserRoles(id));
  }

  /**
   * 覆盖式分配用户角色。
   *
   * <p>PUT /api/v1/users/{id}/roles
   */
  @PutMapping("/{id}/roles")
  @PreAuthorize("hasAuthority('system:user:assign')")
  public Result<Void> assignRoles(@PathVariable Long id, @RequestBody AssignRoleReq req) {
    userRoleService.assignUserRoles(id, req == null ? null : req.getRoleIds());
    return Result.success(null);
  }

  private static UserVO toVO(SystemUser u) {
    UserVO vo = new UserVO();
    vo.setUserId(u.getId());
    vo.setUsername(u.getUsername());
    vo.setEmail(u.getEmail());
    vo.setStatus(u.getStatus());
    vo.setLastLoginAt(u.getLastLoginAt());
    return vo;
  }

  private Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthUserDetails details) {
      return details.userId();
    }
    return null;
  }
}
