package com.ai.api.app.controller;

import com.ai.api.app.dto.AiApplicationControllerDTO;
import com.ai.api.app.entity.AiApplication;
import com.ai.api.app.service.AiApplicationService;
import com.ai.api.audit.annotation.OperationLog;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * AI 应用发布 Controller（V20261121）。
 *
 * <p>两类端点：
 *
 * <ul>
 *   <li>内部管理 CRUD + 发布（需要 JWT 鉴权）
 *   <li>对外统一调用入口 {@code POST /api/v1/app/{appKey}/chat}（**不需要 JWT**，用 API Key 鉴权）
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class AiApplicationController {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final AiApplicationService service;

  // ============ 内部管理（需要 JWT） ============

  @GetMapping("/api/v1/apps")
  public Result<Page<AiApplication>> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(service.list(page, size));
  }

  @GetMapping("/api/v1/apps/{id}")
  public Result<AiApplication> get(@PathVariable Long id) {
    return Result.success(service.get(id));
  }

  @PostMapping("/api/v1/apps")
  @OperationLog(module = "ai-app", action = "create")
  public Result<AiApplication> create(
      @RequestBody AiApplication app, @AuthenticationPrincipal UserDetails user) {
    String username = user != null ? user.getUsername() : "system";
    return Result.success(service.create(app, username));
  }

  @PutMapping("/api/v1/apps/{id}")
  @OperationLog(module = "ai-app", action = "update")
  public Result<AiApplication> update(@PathVariable Long id, @RequestBody AiApplication app) {
    return Result.success(service.update(id, app));
  }

  @DeleteMapping("/api/v1/apps/{id}")
  @OperationLog(module = "ai-app", action = "delete")
  public Result<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return Result.success(null);
  }

  /**
   * 发布 AI 应用：生成 API Key（明文仅返回一次）。
   *
   * <p>⚠️ 密钥只在本次响应返回，请妥善保存；数据库只存 SHA256 哈希。
   */
  @PostMapping("/api/v1/apps/{id}/publish")
  @OperationLog(module = "ai-app", action = "publish")
  public Result<AiApplicationControllerDTO.PublishResponse> publish(@PathVariable Long id) {
    String apiKey = service.publish(id);
    String publicCode = service.get(id).getPublicCode();
    return Result.success(
        AiApplicationControllerDTO.PublishResponse.builder()
            .apiKey(apiKey)
            .message("密钥仅展示一次；扫码入口请使用 publicCode，不要使用 apiKey")
            .publicCode(publicCode)
            .build());
  }

  // ============ 扫码公开入口（publicCode 来自二维码，不暴露 apiKey） ============

  /**
   * 列出当前身份可见的已发布应用，用于小程序“应用”首页。
   *
   * <p>匿名访问只返回 PUBLIC 应用；登录用户额外可见 AUTHENTICATED 应用；角色策略由 SQL 按角色过滤。
   */
  @GetMapping("/api/v1/app/public")
  public Result<AiApplicationControllerDTO.PublicListResponse> listPublic() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Set<String> roles = currentRoles(auth);
    java.util.List<AiApplicationControllerDTO.PublicAppResponse> apps =
        service.listPublished(roles).stream().map(this::toPublicAppResponse).toList();
    return Result.success(
        AiApplicationControllerDTO.PublicListResponse.builder().apps(apps).build());
  }

  /** 获取扫码目标应用的公开配置，用于进入应用前展示名称、类型与访问策略。 */
  @GetMapping("/api/v1/app/public/{publicCode}")
  public Result<AiApplicationControllerDTO.PublicAppResponse> publicApp(
      @PathVariable String publicCode) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    AiApplication app = resolvePublicApp(publicCode, auth);
    return Result.success(toPublicAppResponse(app));
  }

  /** 公开应用同步聊天入口，供不支持 SSE 的轻量客户端使用。 */
  @PostMapping("/api/v1/app/public/{publicCode}/chat")
  public Result<AiApplicationControllerDTO.PublicChatResponse> publicChat(
      @PathVariable String publicCode,
      @jakarta.validation.Valid @RequestBody AiApplicationControllerDTO.PublicChatRequest req) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    AiApplication app = resolvePublicApp(publicCode, auth);
    String reply = service.chat(app, req.getMessage(), req.getHistory());
    return Result.success(
        AiApplicationControllerDTO.PublicChatResponse.builder().reply(reply).build());
  }

  /** 公开应用 SSE 聊天入口，事件协议为 message / done / error。 */
  @PostMapping(
      value = "/api/v1/app/public/{publicCode}/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<org.springframework.http.codec.ServerSentEvent<String>> publicStream(
      @PathVariable String publicCode,
      @jakarta.validation.Valid @RequestBody AiApplicationControllerDTO.PublicStreamRequest req) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    AiApplication app = resolvePublicApp(publicCode, auth);
    return Mono.fromCallable(() -> service.chat(app, req.getMessage(), req.getHistory()))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(reply -> Flux.just(messageEvent(reply), doneEvent()))
        .onErrorResume(error -> Flux.just(errorEvent(error)));
  }

  // ============ 对外统一调用入口（**不需要 JWT**，SecurityConfig 需加白名单） ============

  @Data
  static class ChatReq {
    /** 用户消息（简单格式，与 messages 二选一） */
    String message;

    /** OpenAI 兼容格式：[{role, content}] 消息数组（最后一条作为本轮输入，其余为多轮历史） */
    java.util.List<Map<String, String>> messages;

    /** 可选：历史多轮消息列表（简单格式，与 messages 互斥） */
    java.util.List<Map<String, String>> history;
  }

  /**
   * OpenAI Chat Completions 兼容入口。
   *
   * <p>路径中的 {@code appKey} 即发布时返回的明文 API Key；内部以 SHA256 哈希查库鉴权。
   *
   * <p>返回结构对齐 OpenAI：{@code {"choices":[{"message":{"role":"assistant","content":"..."}}]}}
   */
  @PostMapping("/api/v1/app/{appKey}/chat")
  public Map<String, Object> chat(@PathVariable String appKey, @RequestBody ChatReq req) {
    // 1. API Key 鉴权
    AiApplication app = service.findByApiKey(appKey);
    if (app == null) {
      Map<String, Object> err = new HashMap<>();
      err.put("error", Map.of("message", "无效或已禁用的 API Key", "type", "invalid_api_key"));
      return err;
    }

    // 2. 归一化输入：messages（OpenAI 格式）优先，回退 message + history
    String userMessage = req.message;
    java.util.List<Map<String, String>> history = req.history;
    if (req.messages != null && !req.messages.isEmpty()) {
      Map<String, String> last = req.messages.get(req.messages.size() - 1);
      userMessage = last.getOrDefault("content", "");
      history = req.messages.subList(0, req.messages.size() - 1);
    }
    String reply = service.chat(app, userMessage, history);

    // 3. OpenAI 兼容响应
    Map<String, Object> message = new HashMap<>();
    message.put("role", "assistant");
    message.put("content", reply);

    Map<String, Object> choice = new HashMap<>();
    choice.put("index", 0);
    choice.put("message", message);
    choice.put("finish_reason", "stop");

    Map<String, Object> response = new HashMap<>();
    response.put("id", "chatcmpl-" + Instant.now().toEpochMilli());
    response.put("object", "chat.completion");
    response.put("created", Instant.now().toEpochMilli());
    response.put("model", app.getType() + "-app");
    response.put("choices", java.util.List.of(choice));
    response.put("usage", Map.of("prompt_tokens", 0, "completion_tokens", 0, "total_tokens", 0));
    return response;
  }

  /** 从 JWT 认证主体提取角色编码；匿名访问返回空集合。 */
  private Set<String> currentRoles(Authentication auth) {
    if (auth != null
        && auth.getPrincipal() instanceof AuthUserDetails details
        && details.roles() != null) {
      return details.roles();
    }
    return Set.of();
  }

  /** 构造 SSE 完成事件。 */
  private ServerSentEvent<String> doneEvent() {
    return ServerSentEvent.<String>builder().event("done").data("[DONE]").build();
  }

  /** 构造 SSE 错误事件；只返回简短错误文案，不暴露堆栈。 */
  private ServerSentEvent<String> errorEvent(Throwable error) {
    String message = error.getMessage() == null ? "应用调用失败" : error.getMessage();
    return ServerSentEvent.<String>builder()
        .event("error")
        .data(toJson(Map.of("error", message)))
        .build();
  }

  /** 构造 SSE 消息事件，data 使用 JSON 以支持多行回答。 */
  private ServerSentEvent<String> messageEvent(String reply) {
    return ServerSentEvent.<String>builder()
        .event("message")
        .data(toJson(Map.of("content", reply)))
        .build();
  }

  /**
   * 校验扫码目标应用存在且当前身份满足访问策略。
   *
   * <p>权限校验在 Controller 完成，Service 只负责返回领域数据。
   */
  private AiApplication resolvePublicApp(String publicCode, Authentication auth) {
    AiApplication app = service.findByPublicCode(publicCode);
    if (app == null) {
      throw new BusinessException(ErrorCode.COMMON_NOT_FOUND, "应用不存在或未发布");
    }
    String policy =
        app.getAccessPolicy() == null
            ? AiApplicationService.ACCESS_POLICY_AUTHENTICATED
            : app.getAccessPolicy();
    return switch (policy) {
      case AiApplicationService.ACCESS_POLICY_PUBLIC -> app;
      case AiApplicationService.ACCESS_POLICY_AUTHENTICATED -> {
        if (!(auth != null && auth.getPrincipal() instanceof AuthUserDetails)) {
          throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "请先登录后再使用该应用");
        }
        yield app;
      }
      case AiApplicationService.ACCESS_POLICY_ROLE_BASED -> {
        if (app.getRequiredRole() == null || !currentRoles(auth).contains(app.getRequiredRole())) {
          throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "当前角色无权访问该应用");
        }
        yield app;
      }
      default -> throw new BusinessException(ErrorCode.COMMON_BAD_PARAM, "未知应用访问策略");
    };
  }

  /** 序列化 SSE data，避免手工拼接 JSON。 */
  private String toJson(Map<String, String> data) {
    try {
      return JSON.writeValueAsString(data);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("SSE 事件序列化失败", error);
    }
  }

  /** 领域实体转公开响应，排除 apiKeyHash 与内部配置。 */
  private AiApplicationControllerDTO.PublicAppResponse toPublicAppResponse(AiApplication app) {
    return AiApplicationControllerDTO.PublicAppResponse.builder()
        .accessPolicy(app.getAccessPolicy())
        .appType(app.getType())
        .description(app.getDescription())
        .name(app.getName())
        .publicCode(app.getPublicCode())
        .build();
  }
}
