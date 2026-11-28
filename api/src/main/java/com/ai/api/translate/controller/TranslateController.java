package com.ai.api.translate.controller;

import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.service.ChatMessageService;
import com.ai.api.chat.service.ChatSessionService;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import com.ai.api.translate.TranslationService;
import com.ai.api.translate.dto.TranslateDTO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨语言翻译 Controller。
 *
 * <p>当前提供「单条消息翻译」：移动端在消息操作区点「译」，把该条消息译成目标语言并即时展示。 归属校验复用会话维度（消息 → 会话 → 当前用户），非本人消息一律 403。
 *
 * @author cross-language
 */
@RestController
@RequestMapping("/api/v1/chat")
public class TranslateController {

  private final TranslationService translationService;
  private final ChatMessageService messageService;

  private final ChatSessionService sessionService;

  public TranslateController(
      TranslationService translationService,
      ChatMessageService messageService,
      ChatSessionService sessionService) {
    this.translationService = translationService;
    this.messageService = messageService;
    this.sessionService = sessionService;
  }

  /** 翻译单条消息。POST /api/v1/chat/messages/{messageId}/translate */
  @PostMapping("/messages/{messageId}/translate")
  public Result<TranslateDTO.MessageTranslateResponse> translateMessage(
      @PathVariable Long messageId,
      @RequestBody(required = false) TranslateDTO.MessageTranslateRequest req) {
    Long userId = currentUserId();
    ChatMessage message = messageService.getMessage(messageId);
    if (message == null || !sessionService.belongsToUser(message.getSessionId(), userId)) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    String content = message.getContent();
    if (content == null || content.isBlank()) {
      throw new BusinessException(ErrorCode.COMMON_BAD_PARAM, "消息内容为空，无法翻译");
    }
    String requested = req == null ? null : req.getTargetLang();
    String targetLang =
        requested == null || requested.isBlank()
            ? translationService.inferTargetLang(content)
            : TranslationService.normalizeTargetLang(requested);
    String translated = translationService.translate(content, targetLang);
    return Result.success(
        TranslateDTO.MessageTranslateResponse.builder()
            .messageId(messageId)
            .sourceLang(translationService.inferSourceLang(content))
            .targetLang(targetLang)
            .translated(translated)
            .build());
  }

  private Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails details)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }
    return details.userId();
  }
}
