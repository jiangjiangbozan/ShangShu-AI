package com.ai.api.chat.service;

import com.ai.api.chat.cache.ChatHistoryCacheService;
import com.ai.api.chat.cache.ChatHistorySnapshot;
import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.mapper.ChatMessageMapper;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.CursorPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话消息管理 Service
 *
 * <p>依据 spec.md "消息历史查询" 与 "上下文窗口管理"。
 *
 * @author add-chat-module
 */
@Service
public class ChatMessageService {

  private final ChatMessageMapper messageMapper;
  private final ChatHistoryCacheService chatHistoryCacheService;

  public ChatMessageService(
      ChatMessageMapper messageMapper, ChatHistoryCacheService chatHistoryCacheService) {
    this.messageMapper = messageMapper;
    this.chatHistoryCacheService = chatHistoryCacheService;
  }

  /**
   * 保存消息。
   *
   * @param message 消息实体
   * @return 保存后的消息
   */
  @Transactional
  public ChatMessage saveMessage(ChatMessage message) {
    messageMapper.insert(message);
    chatHistoryCacheService.evict(message.getSessionId());
    return message;
  }

  /**
   * 更新消息内容（用于流式 SSE 占位消息持久化）。
   *
   * <p>配合 ChatStreamService 的"预创建占位 + 完成后更新"策略：
   *
   * <ol>
   *   <li>流开始时 saveMessage 写入 content='' 的占位行
   *   <li>流结束时 updateMessage 写入完整 content 与 latency
   *   <li>异常断流时 updateMessage 写入 partial content + [流式中断] 标记
   * </ol>
   *
   * @param message 仅 id + 待更新字段非空即可
   * @return 更新后的消息
   */
  @Transactional
  public ChatMessage updateMessage(ChatMessage message) {
    messageMapper.updateById(message);
    chatHistoryCacheService.evict(message.getSessionId());
    return message;
  }

  /**
   * 查询会话消息历史（游标分页，Dify 式"先最新一屏 + 向上翻页"）。
   *
   * <p>与 Dify {@code pagination_by_first_id} 一致：
   *
   * <ul>
   *   <li>{@code beforeId == null}：返回最新 {@code limit} 条（按时间正序），即"最新一屏"；走 Redis 缓存（{@link
   *       ChatHistorySnapshot}），未命中回源 MySQL 并回填。
   *   <li>{@code beforeId != null}：返回该消息之前（更早）的 {@code limit} 条，用于向上滚动加载更早历史； 直查 MySQL
   *       不走缓存（缓存仅覆盖最新一屏）。
   * </ul>
   *
   * <p>游标条件 {@code (create_time < before.create_time) OR (create_time = before.create_time AND id <
   * before.id)}，用 {@code limit + 1} 技巧判断 {@code hasMore}，并排除参考记录本身防重复。
   *
   * @param sessionId 会话 ID
   * @param beforeId 已加载消息中最旧一条的 ID（首屏传 null）
   * @param limit 每页大小
   * @return 游标分页结果（消息按时间正序）
   */
  public CursorPage<ChatMessage> getMessagesBySession(Long sessionId, Long beforeId, int limit) {
    if (beforeId == null) {
      ChatHistorySnapshot<ChatMessage> snapshot =
          chatHistoryCacheService.getOrLoad(sessionId, () -> loadTailSnapshot(sessionId, limit));
      return CursorPage.of(snapshot.getData(), limit, snapshot.isHasMore());
    }
    return queryOlderFromDb(sessionId, beforeId, limit);
  }

  /**
   * 获取会话最近 N 条消息（用于上下文窗口）。
   *
   * @param sessionId 会话 ID
   * @param limit 条数
   * @return 消息列表（按 createTime 正序）
   */
  public List<ChatMessage> getRecentMessages(Long sessionId, int limit) {
    List<ChatMessage> messages = messageMapper.selectRecentBySessionId(sessionId, limit);
    // selectRecentBySessionId 返回的是倒序，需要反转为正序
    java.util.Collections.reverse(messages);
    return messages;
  }

  /**
   * 回源加载"最新一屏"消息快照（含 hasMore 标记）。
   *
   * <p>内部先按 {@code create_time DESC, id DESC} 查 {@code limit + 1} 条判断 {@code hasMore}， 再反转为聊天正序返回。
   */
  private ChatHistorySnapshot<ChatMessage> loadTailSnapshot(Long sessionId, int limit) {
    List<ChatMessage> rows = queryDesc(sessionId, null, limit + 1);
    boolean hasMore = rows.size() > limit;
    if (hasMore) {
      rows = rows.subList(0, limit);
    }
    java.util.Collections.reverse(rows);
    return new ChatHistorySnapshot<>(rows, hasMore);
  }

  /** 查询 {@code beforeId} 之前（更早）的消息，直查 MySQL。 */
  private CursorPage<ChatMessage> queryOlderFromDb(Long sessionId, Long beforeId, int limit) {
    ChatMessage before = messageMapper.selectById(beforeId);
    if (before == null || !sessionId.equals(before.getSessionId())) {
      throw new BusinessException(ErrorCode.COMMON_BAD_PARAM, "消息游标无效");
    }
    List<ChatMessage> rows = queryDesc(sessionId, before, limit + 1);
    boolean hasMore = rows.size() > limit;
    if (hasMore) {
      rows = rows.subList(0, limit);
    }
    java.util.Collections.reverse(rows);
    return CursorPage.of(rows, limit, hasMore);
  }

  /**
   * 按 {@code create_time DESC, id DESC} 查询消息。
   *
   * @param cursor 非空时仅返回更早消息（keyset 条件），为空时返回最新 {@code limit} 条
   */
  private List<ChatMessage> queryDesc(Long sessionId, ChatMessage cursor, int limit) {
    LambdaQueryWrapper<ChatMessage> wrapper =
        new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getSessionId, sessionId)
            .orderByDesc(ChatMessage::getCreateTime)
            .orderByDesc(ChatMessage::getId);
    if (cursor != null) {
      wrapper.and(
          w ->
              w.lt(ChatMessage::getCreateTime, cursor.getCreateTime())
                  .or(
                      o ->
                          o.eq(ChatMessage::getCreateTime, cursor.getCreateTime())
                              .lt(ChatMessage::getId, cursor.getId())));
    }
    return messageMapper.selectList(wrapper.last("LIMIT " + limit));
  }
}
