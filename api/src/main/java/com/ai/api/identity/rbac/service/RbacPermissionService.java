package com.ai.api.identity.rbac.service;

import com.ai.api.identity.rbac.entity.SysPermission;
import com.ai.api.identity.rbac.entity.SysRole;
import com.ai.api.identity.rbac.entity.SysRolePermission;
import com.ai.api.identity.rbac.entity.SysUserRole;
import com.ai.api.identity.rbac.mapper.SysPermissionMapper;
import com.ai.api.identity.rbac.mapper.SysRoleMapper;
import com.ai.api.identity.rbac.mapper.SysRolePermissionMapper;
import com.ai.api.identity.rbac.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * RBAC 权限加载服务
 *
 * <p>依据 design.md D3：将用户角色与权限点从 DB 加载为 {@link AuthorityBag}，供 {@code JwtAuthenticationFilter} 注入
 * SecurityContext 的方法级鉴权使用。
 *
 * <ul>
 *   <li>含 {@code SUPER_ADMIN} 角色时返回系统中全部启用权限码（动态，超管自动拥有新增权限）；
 *   <li>结果缓存到 Redis {@code rbac:user:perms:{userId}}（TTL 5min），未命中回源 DB；
 *   <li>角色/权限/用户角色变更时调用 {@link #evictAll()} 或 {@link #evictUsers(Collection)} 失效。
 * </ul>
 *
 * @author add-rbac-management
 */
@Slf4j
@Service
public class RbacPermissionService {

  private static final String KEY_PREFIX = "rbac:user:perms:";
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final SysUserRoleMapper userRoleMapper;
  private final SysRoleMapper roleMapper;
  private final SysRolePermissionMapper rolePermissionMapper;
  private final SysPermissionMapper permissionMapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public RbacPermissionService(
      SysUserRoleMapper userRoleMapper,
      SysRoleMapper roleMapper,
      SysRolePermissionMapper rolePermissionMapper,
      SysPermissionMapper permissionMapper,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper) {
    this.userRoleMapper = userRoleMapper;
    this.roleMapper = roleMapper;
    this.rolePermissionMapper = rolePermissionMapper;
    this.permissionMapper = permissionMapper;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  /** 用户角色与权限包。 */
  public record AuthorityBag(Set<String> roles, Set<String> permissions) {

    public static AuthorityBag empty() {
      return new AuthorityBag(Set.of(), Set.of());
    }
  }

  /**
   * 加载用户角色与权限（优先读缓存）。
   *
   * @param userId 用户 ID
   * @return 角色编码与权限编码集合
   */
  public AuthorityBag loadAuthorities(Long userId) {
    if (userId == null) {
      return AuthorityBag.empty();
    }
    String cacheKey = key(userId);
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (StringUtils.hasText(cached)) {
      return deserialize(cached);
    }
    AuthorityBag bag = loadFromDb(userId);
    try {
      redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(bag), CACHE_TTL);
    } catch (Exception e) {
      log.warn("[loadAuthorities][缓存写入失败 userId={}: {}]", userId, e.getMessage());
    }
    return bag;
  }

  /** 失效指定用户的权限缓存（角色/权限分配变更后调用）。 */
  public void evictUsers(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    userIds.stream().distinct().forEach(id -> redisTemplate.delete(key(id)));
  }

  /** 失效全部用户权限缓存（权限点 CRUD 后调用，影响面较广但低频）。 */
  public void evictAll() {
    Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  private AuthorityBag loadFromDb(Long userId) {
    List<SysUserRole> userRoles =
        userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
    if (userRoles == null || userRoles.isEmpty()) {
      return AuthorityBag.empty();
    }
    List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).distinct().toList();
    List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
    if (roles == null || roles.isEmpty()) {
      return AuthorityBag.empty();
    }
    // 仅启用角色参与鉴权
    List<SysRole> enabledRoles =
        roles.stream().filter(r -> r.getStatus() != null && r.getStatus() == 1).toList();
    if (enabledRoles.isEmpty()) {
      return AuthorityBag.empty();
    }
    Set<String> roleCodes =
        enabledRoles.stream()
            .map(SysRole::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    Set<String> permissionCodes;
    if (enabledRoles.stream().anyMatch(SysRole::isSuperAdmin)) {
      permissionCodes = loadAllEnabledPermissionCodes();
    } else {
      permissionCodes = loadRoleBoundedPermissionCodes(enabledRoles);
    }
    return new AuthorityBag(roleCodes, permissionCodes == null ? Set.of() : permissionCodes);
  }

  private Set<String> loadAllEnabledPermissionCodes() {
    List<SysPermission> perms =
        permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getStatus, 1));
    return perms == null
        ? Set.of()
        : perms.stream()
            .map(SysPermission::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<String> loadRoleBoundedPermissionCodes(List<SysRole> roles) {
    List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
    List<SysRolePermission> rps =
        rolePermissionMapper.selectList(
            new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, roleIds));
    if (rps == null || rps.isEmpty()) {
      return Set.of();
    }
    List<Long> permissionIds =
        rps.stream().map(SysRolePermission::getPermissionId).distinct().toList();
    List<SysPermission> perms =
        permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getId, permissionIds)
                .eq(SysPermission::getStatus, 1));
    return perms == null
        ? Set.of()
        : perms.stream()
            .map(SysPermission::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private AuthorityBag deserialize(String json) {
    try {
      return objectMapper.readValue(json, AuthorityBag.class);
    } catch (Exception e) {
      log.warn("[deserialize][权限缓存解析失败，回源 DB：{}]", e.getMessage());
      return AuthorityBag.empty();
    }
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
