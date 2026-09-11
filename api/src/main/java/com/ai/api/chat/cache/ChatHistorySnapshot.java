package com.ai.api.chat.cache;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话历史缓存快照（add-dify-cursor-pagination）。
 *
 * <p>在"先最新一屏"的加载方式下，缓存不仅需要返回最新的 {@code limit} 条消息（按时间正序）， 还需要携带 {@code hasMore} 标记，让首次加载无需额外 COUNT
 * 查询即可知道是否还有更早的消息。
 *
 * @author add-dify-cursor-pagination
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistorySnapshot<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 最新一屏消息（按 createTime 正序） */
  private List<T> data;

  /** 是否还有更早的历史消息 */
  private boolean hasMore;
}
