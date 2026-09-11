package com.ai.api.moderation.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 内容审查配置（A4）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_moderation_config")
public class ModerationConfig extends BaseDO {

  private String name;

  /** 审查方式: openai / keyword / disabled */
  private String provider;

  private String apiKey;

  private String apiUrl;

  /** 敏感分数阈值 0-1（openai 模式） */
  private BigDecimal threshold;

  /** 敏感词，逗号分隔（keyword 模式） */
  private String keywords;

  /** 是否生效 */
  private Boolean enabled;

  private String remark;
}
