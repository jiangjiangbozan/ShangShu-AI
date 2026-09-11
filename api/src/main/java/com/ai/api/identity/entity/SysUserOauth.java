package com.ai.api.identity.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 第三方 OAuth 账号绑定实体（对应 {@code sys_user_oauth} 表）
 *
 * <p>记录第三方平台用户与本地 {@link SystemUser} 的一对一映射，登录时按 (provider, openId) 唯一定位本地账号。
 *
 * @author add-oauth-gitee
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_oauth")
public class SysUserOauth extends BaseDO {

  @Serial private static final long serialVersionUID = 1L;

  /** 本地用户 ID（sys_user.id） */
  private Long userId;

  /** 第三方平台标识，如 gitee */
  private String provider;

  /** 平台侧用户唯一标识 */
  private String openId;

  /** 跨应用统一标识（预留，Gitee 无） */
  private String unionId;

  /** 平台昵称 */
  private String nickname;

  /** 平台头像地址 */
  private String avatarUrl;

  /** 平台返回的原始用户信息快照（JSON） */
  private String rawProfile;
}
