package com.ai.api.cs.controller;

import com.ai.api.common.result.Result;
import com.ai.api.cs.entity.CsLabel;
import com.ai.api.cs.workbench.LabelService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服标注回流 Controller（add-customer-service）。
 *
 * <p>提供标注提交与查询接口。路径独立于 SessionController 避免冲突。
 *
 * @author add-customer-service
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cs/labels")
@RequiredArgsConstructor
public class LabelController {

  private final LabelService labelService;

  /** 提交标注。 */
  @PostMapping("/{sessionId}")
  public Result<Void> label(@PathVariable Long sessionId, @RequestBody LabelRequest req) {
    Long userId = getCurrentUserId();
    labelService.label(sessionId, req.labelType(), req.content(), userId);
    return Result.success();
  }

  /** 查询会话所有标注。 */
  @GetMapping("/{sessionId}")
  public Result<List<CsLabel>> labels(@PathVariable Long sessionId) {
    return Result.success(labelService.listBySession(sessionId));
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

  /** 标注请求 DTO。 */
  public record LabelRequest(String labelType, String content) {}
}
