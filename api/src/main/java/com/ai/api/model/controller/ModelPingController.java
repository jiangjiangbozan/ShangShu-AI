package com.ai.api.model.controller;

import com.ai.api.common.result.Result;
import com.ai.api.model.service.ModelPingResult;
import com.ai.api.model.service.ModelPingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型连通性测试 Controller
 *
 * <p>依据 design.md D4：对外暴露 {@code POST /api/v1/model/ping}，入参 {@link ModelPingReqVO}， 返回 {@code
 * Result<ModelPingResult>}。
 *
 * <p>所有 Controller 返回必须使用 {@link Result} 包装（见《企业Java后端开发规范.md》§3.2）。
 *
 * @author bootstrap-project
 */
@Slf4j
@Tag(name = "模型管理", description = "模型连通性测试、模型配置管理")
@RestController
@RequestMapping("/api/v1/model")
public class ModelPingController {

  @Resource private ModelPingService modelPingService;

  /**
   * 模型连通性测试。
   *
   * @param reqVO 请求参数
   * @return Result<ModelPingResult>
   */
  @Operation(summary = "模型连通性测试", description = "向指定 modelId 发送 prompt，返回模型响应与耗时")
  @PostMapping("/ping")
  public Result<ModelPingResult> ping(@Valid @RequestBody ModelPingReqVO reqVO) {
    log.info(
        "[ping][modelId({}) prompt length({})]",
        reqVO.getModelId(),
        reqVO.getPrompt() == null ? 0 : reqVO.getPrompt().length());
    ModelPingResult result = modelPingService.ping(reqVO.getModelId(), reqVO.getPrompt());
    return Result.success(result);
  }
}
