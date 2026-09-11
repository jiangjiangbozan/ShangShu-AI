package com.ai.api.chat.controller;

import com.ai.api.chat.dto.CreateSessionReqDTO;
import com.ai.api.chat.dto.MessageRespDTO;
import com.ai.api.chat.dto.SendMessageReqDTO;
import com.ai.api.chat.dto.SessionRespDTO;
import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.entity.ChatSession;
import com.ai.api.chat.service.ChatMessageService;
import com.ai.api.chat.service.ChatSessionService;
import com.ai.api.chat.service.ChatStreamService;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.CursorPage;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 对话 Controller
 *
 * <p>依据 spec.md "会话管理"、"消息发送与 SSE 流式响应"、"消息历史查询"。路径前缀 {@code /api/v1/chat}。
 *
 * @author add-chat-module
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

  private final ChatSessionService sessionService;
  private final ChatMessageService messageService;
  private final ChatStreamService streamService;

  public ChatController(
      ChatSessionService sessionService,
      ChatMessageService messageService,
      ChatStreamService streamService) {
    this.sessionService = sessionService;
    this.messageService = messageService;
    this.streamService = streamService;
  }

  /** 创建新会话 */
  @PostMapping("/sessions")
  public Result<SessionRespDTO> createSession(@Valid @RequestBody CreateSessionReqDTO req) {
    Long userId = getCurrentUserId();
    ChatSession session = sessionService.createSession(userId, req.getModelId(), req.getTitle());
    return Result.success(toSessionResp(session));
  }

  /** 查询用户会话列表（游标分页，Dify 式 keyset） */
  @GetMapping("/sessions")
  public Result<CursorPage<SessionRespDTO>> listSessions(
      @RequestParam(value = "lastId", required = false) Long lastId,
      @RequestParam(defaultValue = "20") int limit) {
    Long userId = getCurrentUserId();
    limit = clampLimit(limit, 100);
    CursorPage<ChatSession> page = sessionService.listSessions(userId, lastId, limit);
    List<SessionRespDTO> list =
        page.getData().stream().map(this::toSessionResp).collect(Collectors.toList());
    return Result.success(CursorPage.of(list, limit, page.getHasMore()));
  }

  /** 删除会话 */
  @DeleteMapping("/sessions/{sessionId}")
  public Result<Void> deleteSession(@PathVariable Long sessionId) {
    Long userId = getCurrentUserId();
    if (!sessionService.belongsToUser(sessionId, userId)) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    sessionService.deleteSession(sessionId);
    return Result.success();
  }

  /** 查询会话消息历史（游标分页：先最新一屏，beforeId 向上加载更早） */
  @GetMapping("/sessions/{sessionId}/messages")
  public Result<CursorPage<MessageRespDTO>> getMessages(
      @PathVariable Long sessionId,
      @RequestParam(value = "beforeId", required = false) Long beforeId,
      @RequestParam(defaultValue = "50") int limit) {
    Long userId = getCurrentUserId();
    if (!sessionService.belongsToUser(sessionId, userId)) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    limit = clampLimit(limit, 200);
    CursorPage<ChatMessage> page = messageService.getMessagesBySession(sessionId, beforeId, limit);
    List<MessageRespDTO> list =
        page.getData().stream().map(this::toMessageResp).collect(Collectors.toList());
    return Result.success(CursorPage.of(list, limit, page.getHasMore()));
  }

  /** SSE 流式发送消息 */
  @PostMapping(
      value = "/sessions/{sessionId}/messages/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> sendMessageStream(
      @PathVariable Long sessionId,
      @Valid @RequestBody SendMessageReqDTO req,
      @RequestParam(value = "kbId", required = false) Long kbId) {
    Long userId = getCurrentUserId();
    if (!sessionService.belongsToUser(sessionId, userId)) {
      return Flux.just(
          ServerSentEvent.<String>builder().event("error").data("{\"error\":\"无权访问该会话\"}").build());
    }
    ChatSession session = sessionService.getSession(sessionId);
    boolean webSearch = Boolean.TRUE.equals(req.getWebSearch());
    return streamService.streamMessage(
        sessionId, userId, session.getModelId(), req.getContent(), kbId, webSearch);
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails details)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }
    return details.userId();
  }

  /** 钳制游标分页每页大小到 [1, max]，避免异常入参导致超大查询。 */
  private int clampLimit(int limit, int max) {
    return Math.max(1, Math.min(limit, max));
  }

  private SessionRespDTO toSessionResp(ChatSession s) {
    return SessionRespDTO.builder()
        .sessionId(s.getId())
        .title(s.getTitle())
        .modelId(s.getModelId())
        .messageCount(s.getMessageCount())
        .totalTokens(s.getTotalTokens())
        .createTime(s.getCreateTime())
        .updateTime(s.getUpdateTime())
        .build();
  }

  private MessageRespDTO toMessageResp(ChatMessage m) {
    return MessageRespDTO.builder()
        .messageId(m.getId())
        .sessionId(m.getSessionId())
        .role(m.getRole())
        .content(m.getContent())
        .promptTokens(m.getPromptTokens())
        .completionTokens(m.getCompletionTokens())
        .totalTokens(m.getTotalTokens())
        .latencyMs(m.getLatencyMs())
        .modelId(m.getModelId())
        .createTime(m.getCreateTime())
        .build();
  }
}
