package com.ai.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建会话请求 DTO
 *
 * @author add-chat-module
 */
@Data
public class CreateSessionReqDTO {

  @NotBlank(message = "模型 ID 不能为空")
  @Size(max = 64, message = "模型 ID 长度不能超过 64 字符")
  private String modelId;

  @Size(max = 128, message = "标题长度不能超过 128 字符")
  private String title;
}
