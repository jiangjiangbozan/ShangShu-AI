package com.ai.api.character.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色卡定义 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("character_card")
public class CharacterCard extends BaseDO {

  /** 创建者用户 ID，0 表示系统预设 */
  private Long userId;

  /** 角色名称 */
  private String name;

  /** 头像 URL 或 emoji */
  private String avatar;

  /** 角色简述 */
  private String description;

  /** 角色设定 / 系统提示词 */
  private String systemPrompt;

  /** 开场白 */
  private String openingStatement;

  /** 标签，逗号分隔 */
  private String tags;

  /** 是否公开 */
  private Boolean isPublic;

  /** 是否为默认角色卡 */
  private Boolean isDefault;

  /** 使用次数 */
  private Integer useCount;

  /** 点赞数 */
  private Integer likeCount;
}
