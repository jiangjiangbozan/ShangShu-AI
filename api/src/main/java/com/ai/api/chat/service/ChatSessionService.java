package com.ai.api.chat.service;

import com.ai.api.chat.entity.ChatSession;
import com.ai.api.chat.mapper.ChatSessionMapper;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.CursorPage;
import com.ai.api.model.config.ChatClientFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话会话管理 Service
 *
 * <p>依据 spec.md "会话管理"：创建、查询、删除会话。 自动命名：用户首轮问答后，调用 LLM 异步生成简短标题。
 *
 * @author add-chat-module
 */
@Slf4j
@Service
public class ChatSessionService {

  private final ChatSessionMapper sessionMapper;
  private final ChatClientFactory chatClientFactory;
  private final Map<String, ChatClient> chatClientRegistry;

  public ChatSessionService(
      ChatSessionMapper sessionMapper,
      ChatClientFactory chatClientFactory,
      Map<String, ChatClient> chatClientRegistry) {
    this.sessionMapper = sessionMapper;
    this.chatClientFactory = chatClientFactory;
    this.chatClientRegistry = chatClientRegistry;
  }

  /**
   * 创建新会话。
   *
   * @param userId 用户 ID
   * @param modelId 模型 ID
   * @param title 会话标题（可选，为空时自动生成"新对话"）
   * @return 创建的会话
   */
  @Transactional
  public ChatSession createSession(Long userId, String modelId, String title) {
    ChatSession session = new ChatSession();
    session.setUserId(userId);
    session.setModelId(modelId);
    session.setTitle(title != null && !title.isBlank() ? title : "新对话");
    session.setContextSize(10);
    session.setMessageCount(0);
    session.setTotalTokens(0L);
    sessionMapper.insert(session);
    return session;
  }

  /**
   * 自动生成会话标题。 <br>
   * 用 LLM 基于用户首条消息生成 ≤10 字标题。失败时降级为截断标题（前 16 字 + "…"）。 <br>
   * 标题生成 prompt 简短，调用量小，不影响主对话链路。
   *
   * <p>模型选择：优先用会话的 modelId 路由到对应 ChatClient，未命中时 fallback 到任意已注册 ChatClient， 全部不可用时降级为截断标题。
   *
   * @param sessionId 会话 ID
   * @param userMessage 用户首条消息内容
   */
  public void generateTitleAsync(Long sessionId, String userMessage) {
    // 截取前 200 字，避免 prompt 过长
    String snippet = userMessage.length() > 200 ? userMessage.substring(0, 200) : userMessage;

    // 仅当原标题仍为初始值 "新对话" 时才更新，避免覆盖用户已设置的标题
    ChatSession session = sessionMapper.selectById(sessionId);
    if (session == null || !"新对话".equals(session.getTitle())) {
      return;
    }

    // 兜底标题：前 16 字 + "…"（中文）
    String fallbackTitle = buildFallbackTitle(snippet);

    ChatClient client = resolveChatClient(session.getModelId());
    if (client == null) {
      log.warn("[generateTitleAsync][no chat client, sessionId({}) use fallback]", sessionId);
      updateTitle(session, fallbackTitle);
      return;
    }

    String prompt = "请为以下用户提问生成一个简短对话标题，要求：4-10 个汉字，不要标点和引号，直接输出标题文本。\n\n用户提问：" + snippet;
    try {
      String title = client.prompt().user(prompt).call().content();
      if (title == null || title.isBlank()) {
        log.warn("[generateTitleAsync][empty llm response, sessionId({}) use fallback]", sessionId);
        updateTitle(session, fallbackTitle);
        return;
      }
      // 清理 LLM 可能附加的引号/换行
      title = title.trim().replaceAll("[\"'\\n\\r]", "");
      if (title.length() > 32) title = title.substring(0, 32);
      if (title.isEmpty()) {
        updateTitle(session, fallbackTitle);
        return;
      }
      updateTitle(session, title);
      log.info("[generateTitleAsync][sessionId({}) title({})]", sessionId, title);
    } catch (Exception e) {
      log.warn(
          "[generateTitleAsync][sessionId({}) llm failed({}) use fallback]",
          sessionId,
          e.getMessage());
      updateTitle(session, fallbackTitle);
    }
  }

  /** 兜底标题：截取前 16 字 + "…"（截断时才加省略号）。 */
  private String buildFallbackTitle(String snippet) {
    String trimmed = snippet.replaceAll("[\\n\\r]+", " ").trim();
    if (trimmed.length() <= 16) {
      return trimmed.isEmpty() ? "新对话" : trimmed;
    }
    return trimmed.substring(0, 16) + "…";
  }

  /** 更新会话标题（仅在 title 仍为 "新对话" 时执行，避免并发首轮重复写入）。 */
  private void updateTitle(ChatSession session, String title) {
    // 再次校验，防止异步并发覆盖
    ChatSession fresh = sessionMapper.selectById(session.getId());
    if (fresh == null || !"新对话".equals(fresh.getTitle())) {
      return;
    }
    fresh.setTitle(title);
    sessionMapper.updateById(fresh);
  }

  /**
   * 按 modelId 路由到对应 ChatClient。
   *
   * <p>统一由 {@code ChatClientFactory.getChatClient(modelId)} 解析（动态注册表按 modelId 热建 → 静态 provider Bean
   * fallback）。未命中时回退到单 provider 部署兜底。
   *
   * @param modelId 模型 ID
   * @return ChatClient，未找到返回 null
   */
  private ChatClient resolveChatClient(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      // 未指定 modelId 时，若只有一种已注册 ChatClient，直接用它（适配仅启用单 provider 的部署）
      if (chatClientRegistry.size() == 1) {
        return chatClientRegistry.values().iterator().next();
      }
      return null;
    }
    // ChatClientFactory 动态路由（动态注册表 → 静态 provider fallback）
    ChatClient client = chatClientFactory.getChatClient(modelId);
    if (client != null) return client;
    // 单 provider 部署兜底
    if (chatClientRegistry.size() == 1) {
      return chatClientRegistry.values().iterator().next();
    }
    return null;
  }

  /**
   * 查询用户会话列表（分页，按 updateTime 倒序）。
   *
   * @param userId 用户 ID
   * @param page 页码
   * @param size 每页大小
   * @return 分页结果
   */
  /**
   * 查询用户会话列表（游标分页，Dify 式 keyset）。
   *
   * <p>按 {@code update_time DESC, id DESC} 排序，用 {@code lastId} 定位上一页最后一条会话： 仅返回 {@code (update_time
   * < last.update_time) OR (update_time = last.update_time AND id < last.id)} 的更早会话。用 {@code limit
   * + 1} 技巧判断 {@code hasMore}（避免 COUNT 查询）。
   *
   * @param userId 用户 ID
   * @param lastId 上一页最后一条会话 ID（首屏传 null）
   * @param limit 每页大小
   * @return 游标分页结果
   */
  public CursorPage<ChatSession> listSessions(Long userId, Long lastId, int limit) {
    LambdaQueryWrapper<ChatSession> wrapper =
        new LambdaQueryWrapper<ChatSession>()
            .eq(ChatSession::getUserId, userId)
            .orderByDesc(ChatSession::getUpdateTime)
            .orderByDesc(ChatSession::getId);
    if (lastId != null) {
      ChatSession last = sessionMapper.selectById(lastId);
      if (last == null || !userId.equals(last.getUserId())) {
        throw new BusinessException(ErrorCode.COMMON_BAD_PARAM, "会话游标无效");
      }
      // keyset 条件：排序键小于上一页最后一条，含 (update_time, id) 组合防重复
      wrapper.and(
          w ->
              w.lt(ChatSession::getUpdateTime, last.getUpdateTime())
                  .or(
                      o ->
                          o.eq(ChatSession::getUpdateTime, last.getUpdateTime())
                              .lt(ChatSession::getId, last.getId())));
    }
    int fetchLimit = limit + 1;
    List<ChatSession> records = sessionMapper.selectList(wrapper.last("LIMIT " + fetchLimit));
    boolean hasMore = records.size() > limit;
    if (hasMore) {
      records = records.subList(0, limit);
    }
    return CursorPage.of(records, limit, hasMore);
  }

  /**
   * 按 ID 查询会话。
   *
   * @param sessionId 会话 ID
   * @return 会话，不存在返回 null
   */
  public ChatSession getSession(Long sessionId) {
    return sessionMapper.selectById(sessionId);
  }

  /**
   * 删除会话（逻辑删除）。
   *
   * @param sessionId 会话 ID
   */
  @Transactional
  public void deleteSession(Long sessionId) {
    sessionMapper.deleteById(sessionId);
  }

  /**
   * 校验会话是否属于指定用户。
   *
   * @param sessionId 会话 ID
   * @param userId 用户 ID
   * @return true=属于该用户
   */
  public boolean belongsToUser(Long sessionId, Long userId) {
    ChatSession session = sessionMapper.selectById(sessionId);
    return session != null && session.getUserId().equals(userId);
  }
}
