package com.ai.api.identity.security;

import java.util.Set;

/**
 * 认证主体（写入 {@code SecurityContext.getAuthentication().getPrincipal()}）
 *
 * <p>避免直接暴露 {@code SystemUser} DO，仅携带 userId、username 与 RBAC 角色/权限（add-rbac-management）几个最小字段， 符合
 * design.md "DO 严格禁止直接对外暴露" 规范。
 *
 * <p>角色为角色编码集合（如 {@code SUPER_ADMIN}），权限为权限点编码集合（如 {@code system:user:delete}）。
 *
 * @author add-user-auth
 */
public record AuthUserDetails(
    Long userId, String username, Set<String> roles, Set<String> permissions) {}
