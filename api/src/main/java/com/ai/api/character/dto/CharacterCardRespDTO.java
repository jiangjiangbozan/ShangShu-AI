package com.ai.api.character.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** 角色卡响应 */
@Data
public class CharacterCardRespDTO {

  private Long id;
  private Long userId;
  private String name;
  private String avatar;
  private String description;
  private String systemPrompt;
  private String openingStatement;
  private String tags;
  private Boolean isPublic;
  private Boolean isDefault;
  private Integer useCount;
  private Integer likeCount;
  private LocalDateTime createTime;
}
