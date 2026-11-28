package com.ai.api.cs.workbench;

import com.ai.api.identity.rbac.entity.SysRole;
import java.util.Set;

/**
 * 客服会话的数据范围（mobile-p0）。
 *
 * <p>管理员（SUPER_ADMIN）可访问全部会话；其余用户仅能访问自己负责或无归属的会话。 范围条件由 Service/Mapper 下推到 SQL，不在内存中过滤。
 *
 * @author mobile-p0
 */
public record SessionScope(boolean admin, Long userId) {

  /**
   * 依据当前登录用户角色构造数据范围。
   *
   * @param userId 当前登录用户 ID
   * @param roles 当前用户角色编码集合
   * @return 会话数据范围
   */
  public static SessionScope of(Long userId, Set<String> roles) {
    boolean admin = roles != null && roles.contains(SysRole.CODE_SUPER_ADMIN);
    return new SessionScope(admin, userId);
  }
}
