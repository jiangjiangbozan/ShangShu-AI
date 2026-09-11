package com.ai.api.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 客服澄清话术模板实体（add-customer-service）。
 *
 * <p>UNIQUE(intent, slot_name) 保证意图 + 槽位组合唯一。
 *
 * @author add-customer-service
 */
@Data
@TableName("cs_clarify_template")
public class CsClarifyTemplate {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String intent;
  private String slotName;
  private String template;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
