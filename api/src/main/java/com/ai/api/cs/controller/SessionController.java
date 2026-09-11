package com.ai.api.cs.controller;

import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.cs.entity.CsSession;
import com.ai.api.cs.workbench.SessionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服会话工作台 Controller（add-customer-service）。
 *
 * <p>提供会话列表、详情、人工接管接口。
 *
 * @author add-customer-service
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cs/sessions")
@RequiredArgsConstructor
public class SessionController {

  private final SessionService sessionService;

  /** 分页查询会话列表。 */
  @GetMapping
  public Result<PageResult<CsSession>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long assignedTo,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    IPage<CsSession> p = sessionService.listSessions(status, assignedTo, page, size);
    PageResult<CsSession> result = new PageResult<>(p.getRecords(), p.getTotal());
    return Result.success(result);
  }

  /** 查询会话详情。 */
  @GetMapping("/{sessionId}")
  public Result<CsSession> get(@PathVariable Long sessionId) {
    CsSession session = sessionService.getBySessionId(sessionId);
    return Result.success(session);
  }

  /** 人工接管。 */
  @PostMapping("/{sessionId}/takeover")
  public Result<Void> takeover(@PathVariable Long sessionId) {
    Long userId = getCurrentUserId();
    sessionService.takeover(sessionId, userId);
    return Result.success();
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserDetails details) {
      try {
        return Long.parseLong(details.getUsername());
      } catch (NumberFormatException e) {
        return 0L;
      }
    }
    return 0L;
  }
}
