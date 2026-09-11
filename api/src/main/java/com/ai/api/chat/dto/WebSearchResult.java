package com.ai.api.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网络搜索结果 DTO（内部处理用，同时通过 SSE search 事件下发给客户端展示引用来源）
 *
 * @author add-web-search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchResult {

  /** 结果标题 */
  private String title;

  /** 结果链接 */
  private String url;

  /** 内容摘要 */
  private String content;

  /** 相关性得分（0~1，越高越相关） */
  private double score;
}
