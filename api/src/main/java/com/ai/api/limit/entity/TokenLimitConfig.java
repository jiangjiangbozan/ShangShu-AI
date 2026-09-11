package com.ai.api.limit.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Token 限额 / 限流配置（A2） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_token_limit_config")
public class TokenLimitConfig extends BaseDO {

  /** 作用域：USER / TENANT / PUBLIC */
  private String scope;

  /** 目标 ID：0=默认（该 scope 全部生效），非 0=具体用户/租户 */
  private Long targetId;

  /** 周期 Token 限额（0=不限） */
  private Long tokenQuota;

  /** 限额周期：DAY / MONTH */
  private String quotaPeriod;

  /** 每分钟请求数限制（0=不限） */
  private Integer rateLimitPerMinute;

  /** 每日请求数限制（0=不限） */
  private Integer rateLimitPerDay;

  /** 状态：active / disabled */
  private String status;

  private String remark;
}
