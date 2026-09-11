package com.ai.api.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 客服会话扩展实体（add-customer-service）。
 *
 * <p>扩展 chat_session 的客服业务字段：status（ai/human/done/failed）+ assigned_to。 独立表避免污染原 chat_session 表结构。
 *
 * @author add-customer-service
 */
@Data
@TableName("cs_session")
public class CsSession {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long sessionId;
  private Long runtimeId;
  private String status;
  private Long assignedTo;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
