package com.ai.api.moderation.controller;

import com.ai.api.audit.annotation.OperationLog;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.moderation.entity.ModerationConfig;
import com.ai.api.moderation.service.ModerationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.io.IOException;
import java.util.Map;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内容审查配置（A4）。 */
@RestController
@RequestMapping("/api/v1/moderation-configs")
public class ModerationController {

  private final ModerationService moderationService;

  public ModerationController(ModerationService moderationService) {
    this.moderationService = moderationService;
  }

  @Data
  static class TestReq {
    private String provider;
    private String apiKey;
    private String apiUrl;
    private String threshold;
    private String keywords;
    private String text;
  }

  /** 分页查询。GET /api/v1/moderation-configs */
  @GetMapping
  public Result<PageResult<ModerationConfig>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String name) {
    IPage<ModerationConfig> result = moderationService.list(page, size, name);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }

  /** 新增。POST /api/v1/moderation-configs */
  @PostMapping
  @OperationLog(module = "moderation", action = "create")
  public Result<ModerationConfig> create(@RequestBody ModerationConfig config) {
    return Result.success(moderationService.create(config));
  }

  /** 更新。PUT /api/v1/moderation-configs/{id} */
  @PutMapping("/{id}")
  @OperationLog(module = "moderation", action = "update")
  public Result<ModerationConfig> update(
      @PathVariable Long id, @RequestBody ModerationConfig config) {
    return Result.success(moderationService.update(id, config));
  }

  /** 删除。DELETE /api/v1/moderation-configs/{id} */
  @DeleteMapping("/{id}")
  @OperationLog(module = "moderation", action = "delete")
  public Result<Void> delete(@PathVariable Long id) {
    moderationService.delete(id);
    return Result.success(null);
  }

  /** 测试审查。POST /api/v1/moderation-configs/test */
  @PostMapping("/test")
  public Result<Map<String, Object>> test(@RequestBody TestReq req)
      throws IOException, InterruptedException {
    ModerationService.ModerationResult result =
        moderationService.test(
            req.getProvider(),
            req.getApiKey(),
            req.getApiUrl(),
            req.getThreshold(),
            req.getKeywords(),
            req.getText());
    return Result.success(Map.of("flagged", result.flagged(), "reason", result.reason()));
  }
}
