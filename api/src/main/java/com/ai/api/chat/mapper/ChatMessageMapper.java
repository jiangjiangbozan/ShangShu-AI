package com.ai.api.chat.mapper;

import com.ai.api.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 对话消息 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，额外声明按会话 ID 查询方法。
 *
 * @author add-chat-module
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

  /**
   * 按会话 ID 查询消息列表（按 createTime 正序）
   *
   * @param sessionId 会话 ID
   * @return 消息列表
   */
  List<ChatMessage> selectBySessionId(@Param("sessionId") Long sessionId);

  /**
   * 查询会话最近 N 条消息（用于上下文窗口截断）
   *
   * @param sessionId 会话 ID
   * @param limit 条数
   * @return 消息列表
   */
  List<ChatMessage> selectRecentBySessionId(
      @Param("sessionId") Long sessionId, @Param("limit") int limit);
}
