package com.ai.api.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建角色卡请求 */
@Data
public class CharacterCardCreateReqDTO {

  @NotBlank(message = "角色名称不能为空")
  @Size(max = 64, message = "角色名称不能超过64个字符")
  private String name;

  @Size(max = 512, message = "头像地址不能超过512个字符")
  private String avatar;

  @Size(max = 512, message = "角色描述不能超过512个字符")
  private String description;

  @NotBlank(message = "角色设定不能为空")
  private String systemPrompt;

  private String openingStatement;

  private String tags;

  /** 是否公开（默认 false） */
  private Boolean isPublic;
}
