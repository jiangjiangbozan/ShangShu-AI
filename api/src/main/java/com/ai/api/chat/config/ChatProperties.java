package com.ai.api.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话模块配置
 *
 * <p>绑定 {@code ai.chat} 配置段，依据 design.md。
 *
 * @author add-chat-module
 */
@Data
@Component
@ConfigurationProperties("ai.chat")
public class ChatProperties {

  /** 上下文窗口大小（轮次），默认 10 */
  private int contextWindowSize = 10;

  /** SSE 流式响应超时时间 */
  private String streamTimeout = "PT5M";

  /** 每日 Token 配额上限 */
  private long maxTokensPerDay = 100000;
}
