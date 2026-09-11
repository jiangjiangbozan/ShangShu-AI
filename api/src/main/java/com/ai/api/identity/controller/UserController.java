package com.ai.api.identity.controller;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.Result;
import com.ai.api.identity.entity.SystemUser;
import com.ai.api.identity.security.AuthUserDetails;
import com.ai.api.identity.service.UserService;
import java.util.Set;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户 Controller
 *
 * <p>依据 design.md "对外暴露接口"：{@code GET /api/v1/users/me} 获取当前登录用户信息。
 *
 * @author add-user-auth
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public Result<UserInfoVO> getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails details)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }
    SystemUser user = userService.getById(details.userId());
    UserInfoVO vo = new UserInfoVO();
    vo.setUserId(user.getId());
    vo.setUsername(user.getUsername());
    vo.setEmail(user.getEmail());
    vo.setStatus(user.getStatus());
    vo.setLastLoginAt(user.getLastLoginAt());
    // RBAC：携带角色编码与权限点编码（无角色/权限时返回空数组）
    vo.setRoles(details.roles() == null ? Set.of() : details.roles());
    vo.setPermissions(details.permissions() == null ? Set.of() : details.permissions());
    return Result.success(vo);
  }

  /** 用户信息 VO（不暴露 password_hash、fail_count、lock_until 等敏感字段）。 */
  @Data
  public static class UserInfoVO {
    private Long userId;
    private String username;
    private String email;
    private Integer status;
    private java.time.LocalDateTime lastLoginAt;
    private Set<String> roles;
    private Set<String> permissions;
  }
}
