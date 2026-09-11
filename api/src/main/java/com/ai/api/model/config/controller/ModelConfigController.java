package com.ai.api.model.config.controller;

import com.ai.api.audit.annotation.OperationLog;
import com.ai.api.common.result.Result;
import com.ai.api.data.entity.SysModelConfig;
import com.ai.api.model.config.dto.ModelConfigReqDTO;
import com.ai.api.model.config.dto.ModelConfigRespDTO;
import com.ai.api.model.config.dto.ModelTestRespDTO;
import com.ai.api.model.config.service.ModelConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型配置管理 Controller
 *
 * <p>依据 spec.md "模型配置管理"：CRUD + 连接测试。路径前缀 {@code /api/v1/model/configs}。
 *
 * @author add-chat-module
 */
@RestController
@RequestMapping("/api/v1/model/configs")
public class ModelConfigController {

  private final ModelConfigService configService;

  public ModelConfigController(ModelConfigService configService) {
    this.configService = configService;
  }

  /** 获取所有模型配置列表 */
  @GetMapping
  public Result<List<ModelConfigRespDTO>> listConfigs() {
    List<SysModelConfig> configs = configService.listConfigs();
    List<ModelConfigRespDTO> list =
        configs.stream().map(this::toRespDTO).collect(Collectors.toList());
    return Result.success(list);
  }

  /** 创建模型配置 */
  @PostMapping
  @OperationLog(module = "model-config", action = "create")
  public Result<ModelConfigRespDTO> createConfig(@Valid @RequestBody ModelConfigReqDTO req) {
    SysModelConfig config = toEntity(req);
    SysModelConfig created = configService.createConfig(config);
    return Result.success(toRespDTO(created));
  }

  /** 更新模型配置 */
  @PutMapping("/{id}")
  @OperationLog(module = "model-config", action = "update")
  public Result<ModelConfigRespDTO> updateConfig(
      @PathVariable Long id, @Valid @RequestBody ModelConfigReqDTO req) {
    SysModelConfig config = toEntity(req);
    SysModelConfig updated = configService.updateConfig(id, config);
    return Result.success(toRespDTO(updated));
  }

  /** 删除模型配置 */
  @DeleteMapping("/{id}")
  @OperationLog(module = "model-config", action = "delete")
  public Result<Void> deleteConfig(@PathVariable Long id) {
    configService.deleteConfig(id);
    return Result.success();
  }

  /** 测试模型 API 连接 */
  @PostMapping("/{id}/test")
  public Result<ModelTestRespDTO> testConnection(@PathVariable Long id) {
    SysModelConfig config = configService.getConfig(id);
    ModelConfigService.ModelConnectionTestResult result = configService.testConnection(config);
    return Result.success(
        ModelTestRespDTO.builder()
            .success(result.success())
            .latencyMs(result.latencyMs())
            .message(result.message())
            .build());
  }

  private SysModelConfig toEntity(ModelConfigReqDTO req) {
    SysModelConfig config = new SysModelConfig();
    config.setProvider(req.getProvider());
    config.setModelId(req.getModelId());
    config.setDisplayName(req.getDisplayName());
    config.setApiKey(req.getApiKey());
    config.setBaseUrl(req.getBaseUrl());
    config.setEnabled(req.getEnabled());
    config.setParameters(req.getParameters());
    config.setSortOrder(req.getSortOrder());
    return config;
  }

  private ModelConfigRespDTO toRespDTO(SysModelConfig config) {
    String plainApiKey = configService.decryptApiKey(config);
    return ModelConfigRespDTO.builder()
        .id(config.getId())
        .provider(config.getProvider())
        .modelId(config.getModelId())
        .displayName(config.getDisplayName())
        .apiKeyMasked(configService.maskApiKey(plainApiKey))
        .baseUrl(config.getBaseUrl())
        .enabled(config.getEnabled())
        .parameters(config.getParameters())
        .sortOrder(config.getSortOrder())
        .createTime(config.getCreateTime())
        .updateTime(config.getUpdateTime())
        .build();
  }
}
