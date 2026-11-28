package com.ai.api.cs.controller;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.cs.entity.CsSession;
import com.ai.api.cs.workbench.SessionScope;
import com.ai.api.cs.workbench.SessionService;
import com.ai.api.identity.security.AuthUserDetails;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服会话工作台 Controller（add-customer-service）。
 *
 * <p>提供会话列表、详情、人工接管接口。mobile-p0：所有查询/写入口均按当前用户角色下推数据范围， 并在 Controller 侧完成权限注解校验。
 *
 * @author add-customer-service
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cs/sessions")
@RequiredArgsConstructor
public class SessionController {

  private final SessionService sessionService;

  /**
   * 从 SecurityContext 构造当前用户的数据范围。
   *
   * @return 当前用户数据范围
   */
  private SessionScope currentScope() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthUserDetails details) {
      return SessionScope.of(details.userId(), details.roles());
    }
    throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
  }

  /** 查询会话详情（无权限访问时返回 null）。 */
  @GetMapping("/{sessionId}")
  @PreAuthorize("hasAuthority('cs:session:list')")
  public Result<CsSession> get(@PathVariable Long sessionId) {
    return Result.success(sessionService.getBySessionId(sessionId, currentScope()));
  }

  /** 分页查询会话列表（管理员全部，客服仅自己负责或无归属）。 */
  @GetMapping
  @PreAuthorize("hasAuthority('cs:session:list')")
  public Result<PageResult<CsSession>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long assignedTo,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    IPage<CsSession> p =
        sessionService.listSessions(status, assignedTo, page, size, currentScope());
    PageResult<CsSession> result = new PageResult<>(p.getRecords(), p.getTotal());
    return Result.success(result);
  }

  /** 人工接管。 */
  @PostMapping("/{sessionId}/takeover")
  @PreAuthorize("hasAuthority('cs:session:takeover')")
  public Result<Void> takeover(@PathVariable Long sessionId) {
    SessionScope scope = currentScope();
    sessionService.takeover(sessionId, scope.userId(), scope);
    return Result.success();
  }
}
