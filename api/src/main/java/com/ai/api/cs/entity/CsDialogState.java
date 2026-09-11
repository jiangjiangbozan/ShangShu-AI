package com.ai.api.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 客服对话状态实体（add-customer-service）。
 *
 * <p>维护槽位填充进度与状态机阶段。{@code state} 取值： collecting|confirming|executing|done。
 *
 * @author add-customer-service
 */
@Data
@TableName(value = "cs_dialog_state", autoResultMap = true)
public class CsDialogState {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long runtimeId;
  private Long sessionId;
  private String intent;

  /** 已填充槽位 JSON，示例 {@code {"region":"北京","product":"iPhone"}}。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String slots;

  private String state;
  private Integer clarifyRounds;
  private Integer fallbackTriggered;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
