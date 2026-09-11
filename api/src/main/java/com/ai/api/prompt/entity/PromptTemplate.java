package com.ai.api.prompt.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template")
public class PromptTemplate extends BaseDO {

  private String name;
  private String category;
  private String content;
  private String variables;
  private String modelId;
  private Boolean isActive;
  private Integer version;
  private String description;
}
