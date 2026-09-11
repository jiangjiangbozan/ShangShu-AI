package com.ai.api.chat.annotation.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 标注问答 DO，对应表 chat_annotation */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_annotation")
public class Annotation extends BaseDO {

  private static final long serialVersionUID = 1L;

  /** 关联消息 ID（可选，从对话日志标注时关联） */
  private Long messageId;

  /** 问题/提问内容 */
  private String question;

  /** 回答内容 */
  private String answer;

  /** 命中次数 */
  private Integer hitCount;

  /** 是否启用：1=是，0=否 */
  private Integer enabled;
}
