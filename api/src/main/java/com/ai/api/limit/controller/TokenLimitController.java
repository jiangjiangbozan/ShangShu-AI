package com.ai.api.limit.controller;

import com.ai.api.audit.annotation.OperationLog;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import com.ai.api.limit.entity.TokenLimitConfig;
import com.ai.api.limit.service.TokenLimitService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Token 限额 / 限流配置管理（A2）。 */
@RestController
@RequestMapping("/api/v1/limits")
public class TokenLimitController {

  private final TokenLimitService tokenLimitService;

  public TokenLimitController(TokenLimitService tokenLimitService) {
    this.tokenLimitService = tokenLimitService;
  }

  @Data
  static class UpdateReq {
    private Long tokenQuota;
    private String quotaPeriod;
    private Integer rateLimitPerMinute;
    private Integer rateLimitPerDay;
    private String status;
    private String remark;
  }

  @Data
  static class CreateReq extends UpdateReq {
    private String scope;
    private Long targetId;
  }

  /** 分页查询配置。GET /api/v1/limits */
  @GetMapping
  public Result<PageResult<TokenLimitConfig>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) Long targetId) {
    IPage<TokenLimitConfig> result = tokenLimitService.list(page, size, scope, targetId);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }

  /** 新增配置。POST /api/v1/limits */
  @PostMapping
  @OperationLog(module = "token-limit", action = "create")
  public Result<TokenLimitConfig> create(@RequestBody CreateReq req) {
    TokenLimitConfig config = new TokenLimitConfig();
    config.setScope(req.getScope());
    config.setTargetId(req.getTargetId());
    config.setTokenQuota(req.getTokenQuota());
    config.setQuotaPeriod(req.getQuotaPeriod());
    config.setRateLimitPerMinute(req.getRateLimitPerMinute());
    config.setRateLimitPerDay(req.getRateLimitPerDay());
    config.setStatus(req.getStatus());
    config.setRemark(req.getRemark());
    return Result.success(tokenLimitService.create(config, currentUsername()));
  }

  /** 更新配置。PUT /api/v1/limits/{id} */
  @PutMapping("/{id}")
  @OperationLog(module = "token-limit", action = "update")
  public Result<TokenLimitConfig> update(@PathVariable Long id, @RequestBody UpdateReq req) {
    TokenLimitConfig config = new TokenLimitConfig();
    config.setTokenQuota(req.getTokenQuota());
    config.setQuotaPeriod(req.getQuotaPeriod());
    config.setRateLimitPerMinute(req.getRateLimitPerMinute());
    config.setRateLimitPerDay(req.getRateLimitPerDay());
    config.setStatus(req.getStatus());
    config.setRemark(req.getRemark());
    return Result.success(tokenLimitService.update(id, config, currentUsername()));
  }

  /** 删除配置。DELETE /api/v1/limits/{id} */
  @DeleteMapping("/{id}")
  @OperationLog(module = "token-limit", action = "delete")
  public Result<Void> delete(@PathVariable Long id) {
    tokenLimitService.delete(id);
    return Result.success(null);
  }

  private String currentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthUserDetails details) {
      return String.valueOf(details.userId());
    }
    return null;
  }
}
