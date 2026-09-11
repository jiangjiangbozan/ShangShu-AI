package com.ai.api.memory.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户长期记忆实体
 *
 * <p>借鉴 Operit 的本地记忆库设计，走服务端持久化路线。 记忆可按分类管理，支持重要度排序和被引用计数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_memory")
public class UserMemory extends BaseDO {

  /** 所属用户 ID */
  private Long userId;

  /** 记忆分类：general/preference/fact/person */
  private String category;

  /** 记忆内容 */
  private String content;

  /** 记忆来源（如会话 ID） */
  private String source;

  /** 重要度 0-10 */
  private Integer importance;

  /** 被引用次数 */
  private Integer timesUsed;
}
