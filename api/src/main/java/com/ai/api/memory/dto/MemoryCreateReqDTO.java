package com.ai.api.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建记忆请求 DTO */
@Data
public class MemoryCreateReqDTO {

  /** 记忆分类：general/preference/fact/person */
  @Size(max = 32, message = "分类长度不能超过 32")
  private String category = "general";

  /** 记忆内容 */
  @NotBlank(message = "记忆内容不能为空")
  @Size(max = 2000, message = "记忆内容不能超过 2000 字")
  private String content;

  /** 记忆来源 */
  @Size(max = 255, message = "来源长度不能超过 255")
  private String source;

  /** 重要度 0-10 */
  private Integer importance = 0;
}
