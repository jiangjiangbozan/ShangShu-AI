package com.ai.api.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 客服意图注册表实体（add-customer-service）。
 *
 * <p>UNIQUE(name) 保证意图名唯一。{@code slots_schema} 定义槽位 schema，示例： {@code
 * {"region":{"required":true},"product":{"required":true,"type":"string"}}}。
 *
 * @author add-customer-service
 */
@Data
@TableName(value = "cs_intent", autoResultMap = true)
public class CsIntent {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String description;

  /** 槽位 schema JSON（原文存储，无需 Jackson 反序列化）。 */
  private String slotsSchema;

  /** 触发关键词列表（逗号分隔，命中即规则判定，可热更新）。 */
  private String keywords;

  private String fallbackIntent;
  private Integer enabled;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
