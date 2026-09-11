package com.ai.api.identity.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体（对应 {@code sys_user} 表）
 *
 * <p>依据 design.md "数据层结构" 与 Flyway V20260819 DDL。继承 {@link BaseDO} 复用审计字段 与逻辑删除。DO
 * 严格禁止直接对外暴露，Controller 必须通过 DTO/VO 转换。
 *
 * @author add-user-auth
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SystemUser extends BaseDO {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户名（3-32 字符，字母数字下划线） */
  private String username;

  /** 邮箱 */
  private String email;

  /** BCrypt 哈希（含盐与版本前缀，长度约 60-72） */
  private String passwordHash;

  /** 状态：1=正常, 0=禁用 */
  private Integer status;

  /** 连续登录失败次数 */
  private Integer failCount;

  /** 锁定截止时间（NULL 表示未锁定） */
  private LocalDateTime lockUntil;

  /** 最近登录时间 */
  private LocalDateTime lastLoginAt;

  /** 令牌版本号：递增使既有 JWT（access + refresh）立即失效，用于改密码/强制下线后的即时作废 */
  private Integer tokenVersion;
}
