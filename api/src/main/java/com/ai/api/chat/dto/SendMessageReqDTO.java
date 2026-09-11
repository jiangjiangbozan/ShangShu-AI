package com.ai.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息请求 DTO
 *
 * @author add-chat-module
 */
@Data
public class SendMessageReqDTO {

  @NotBlank(message = "消息内容不能为空")
  @Size(max = 32768, message = "消息内容过长")
  private String content;

  /** 是否启用网络搜索增强（可选，默认 false；服务端未配置搜索服务时自动忽略） */
  private Boolean webSearch = Boolean.FALSE;
}
