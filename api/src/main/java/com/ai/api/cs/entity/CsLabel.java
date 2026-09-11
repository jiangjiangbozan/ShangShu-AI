package com.ai.api.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 客服标注回流实体（add-customer-service）。
 *
 * <p>客服人员对会话的标注（正/负/中性），用于意图识别模型微调与回流。
 *
 * @author add-customer-service
 */
@Data
@TableName("cs_label")
public class CsLabel {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long sessionId;
  private String labelType;
  private String labelContent;
  private Long labelerId;
  private LocalDateTime createTime;
}
