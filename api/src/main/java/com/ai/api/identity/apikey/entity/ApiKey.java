package com.ai.api.identity.apikey.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** API 密钥 DO，对应表 sys_api_key */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_api_key")
public class ApiKey extends BaseDO {

  private static final long serialVersionUID = 1L;

  /** 密钥名称 */
  private String name;

  /** 密钥前缀（用于展示，如 sk-ai-xxxx） */
  private String keyPrefix;

  /** 密钥哈希（SHA-256，存储时不存明文） */
  private String keyHash;

  /** 密钥明文（仅创建时返回，不持久化） */
  @com.baomidou.mybatisplus.annotation.TableField(exist = false)
  private String keyPlain;

  /** 创建者 ID */
  private Long creatorId;

  /** 最后使用时间 */
  private java.time.LocalDateTime lastUsedAt;

  /** 是否启用：1=是，0=否 */
  private Integer enabled;
}
