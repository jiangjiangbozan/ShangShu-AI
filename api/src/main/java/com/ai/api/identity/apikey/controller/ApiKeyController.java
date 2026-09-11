package com.ai.api.identity.apikey.controller;

import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.identity.apikey.entity.ApiKey;
import com.ai.api.identity.apikey.service.ApiKeyService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** API 密钥管理 Controller */
@Tag(name = "API 密钥管理")
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

  private final ApiKeyService apiKeyService;

  @Operation(summary = "创建 API 密钥")
  @PostMapping
  public Result<ApiKey> create(@RequestBody Map<String, String> body) {
    String name = body.getOrDefault("name", "默认密钥");
    // TODO: 从 Sa-Token 获取当前用户 ID
    ApiKey apiKey = apiKeyService.create(name, 1L);
    return Result.success(apiKey);
  }

  @Operation(summary = "分页查询 API 密钥")
  @GetMapping
  public Result<PageResult<ApiKey>> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    Page<ApiKey> result = apiKeyService.list(page, size);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }

  @Operation(summary = "启用/禁用 API 密钥")
  @PutMapping("/{id}/toggle")
  public Result<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
    apiKeyService.toggleEnabled(id, body.getOrDefault("enabled", true));
    return Result.success();
  }

  @Operation(summary = "删除 API 密钥")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    apiKeyService.delete(id);
    return Result.success();
  }
}
