package com.ai.api.chat.mapper;

import com.ai.api.chat.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 对话会话 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，额外声明按用户 ID 查询方法。
 *
 * @author add-chat-module
 */
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

  /**
   * 按用户 ID 查询会话列表（按 updateTime 倒序）
   *
   * @param userId 用户 ID
   * @return 会话列表
   */
  List<ChatSession> selectByUserId(@Param("userId") Long userId);
}
