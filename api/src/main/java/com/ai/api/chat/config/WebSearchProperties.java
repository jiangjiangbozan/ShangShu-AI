package com.ai.api.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网络搜索增强配置
 *
 * <p>绑定 {@code ai.web-search} 配置段。支持两种搜索提供方：
 *
 * <ul>
 *   <li><b>tavily</b>（默认）：面向 AI 的搜索 API，需配置 API Key（ https://tavily.com 注册获取）
 *   <li><b>searxng</b>：自建 SearXNG 实例（需 format=json 已启用），无需 Key
 * </ul>
 *
 * <p>未配置 API Key 或提供方不可用时，服务端自动降级为普通对话（不阻断主流程）。
 *
 * @author add-web-search
 */
@Data
@Component
@ConfigurationProperties("ai.web-search")
public class WebSearchProperties {

  /** 是否启用网络搜索增强（全局开关，false 时接口传 webSearch=true 也会被忽略） */
  private boolean enabled = false;

  /** 搜索提供方：tavily | searxng */
  private String provider = "tavily";

  /** Tavily API Key（provider=tavily 时必填） */
  private String apiKey = "";

  /** 搜索 API 地址。tavily 默认官方地址；searxng 需配置自建实例地址 */
  private String apiUrl = "https://api.tavily.com/search";

  /** HTTP 请求超时（毫秒），避免搜索拖慢流式响应 */
  private int timeoutMs = 5000;

  /** 每次搜索返回的最大结果数 */
  private int maxResults = 5;

  /** 单条结果摘要注入 prompt 的最大字符数（防止上下文爆炸） */
  private int maxContentChars = 500;
}
