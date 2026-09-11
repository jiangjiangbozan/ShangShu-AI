package com.ai.api.chat.controller;

import com.ai.api.chat.dto.TokenStatsRespDTO;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token 用量统计 Controller
 *
 * <p>依据 spec.md "Token 计数"：从 Redis 读取用户 Token 用量。
 *
 * @author add-chat-module
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatTokenController {

  private final StringRedisTemplate redisTemplate;

  public ChatTokenController(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /** 获取 Token 用量统计 */
  @GetMapping("/tokens/stats")
  public Result<TokenStatsRespDTO> getTokenStats(
      @RequestParam(defaultValue = "today") String period) {
    Long userId = getCurrentUserId();
    String key = "chat:token:" + userId + ":" + period;
    String val = redisTemplate.opsForValue().get(key);
    long tokens = val != null ? Long.parseLong(val) : 0L;
    return Result.success(TokenStatsRespDTO.builder().period(period).totalTokens(tokens).build());
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthUserDetails details) {
      return details.userId();
    }
    return 0L;
  }
}
