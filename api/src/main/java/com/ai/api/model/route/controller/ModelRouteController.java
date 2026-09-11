package com.ai.api.model.route.controller;

import com.ai.api.audit.annotation.OperationLog;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import com.ai.api.model.route.entity.ModelRoutePolicy;
import com.ai.api.model.route.service.ModelRouteService;
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

/** 模型路由 / 降级策略配置管理（A5）。 */
@RestController
@RequestMapping("/api/v1/model-routes")
public class ModelRouteController {

  private final ModelRouteService modelRouteService;

  public ModelRouteController(ModelRouteService modelRouteService) {
    this.modelRouteService = modelRouteService;
  }

  @Data
  static class Req {
    private String modelId;
    private String fallbackModelId;
    private Integer priority;
    private String status;
    private String remark;
  }

  /** 分页查询。GET /api/v1/model-routes */
  @GetMapping
  public Result<PageResult<ModelRoutePolicy>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String modelId) {
    IPage<ModelRoutePolicy> result = modelRouteService.list(page, size, modelId);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }

  /** 新增策略。POST /api/v1/model-routes */
  @PostMapping
  @OperationLog(module = "model-route", action = "create")
  public Result<ModelRoutePolicy> create(@RequestBody Req req) {
    ModelRoutePolicy policy = new ModelRoutePolicy();
    policy.setModelId(req.getModelId());
    policy.setFallbackModelId(req.getFallbackModelId());
    policy.setPriority(req.getPriority());
    policy.setStatus(req.getStatus());
    policy.setRemark(req.getRemark());
    return Result.success(modelRouteService.create(policy, currentUsername()));
  }

  /** 更新策略。PUT /api/v1/model-routes/{id} */
  @PutMapping("/{id}")
  @OperationLog(module = "model-route", action = "update")
  public Result<ModelRoutePolicy> update(@PathVariable Long id, @RequestBody Req req) {
    ModelRoutePolicy policy = new ModelRoutePolicy();
    policy.setModelId(req.getModelId());
    policy.setFallbackModelId(req.getFallbackModelId());
    policy.setPriority(req.getPriority());
    policy.setStatus(req.getStatus());
    policy.setRemark(req.getRemark());
    return Result.success(modelRouteService.update(id, policy, currentUsername()));
  }

  /** 删除策略。DELETE /api/v1/model-routes/{id} */
  @DeleteMapping("/{id}")
  @OperationLog(module = "model-route", action = "delete")
  public Result<Void> delete(@PathVariable Long id) {
    modelRouteService.delete(id);
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
