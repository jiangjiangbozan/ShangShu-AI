package com.ai.api.model.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;

/**
 * 模型连通性测试请求 VO
 *
 * <p>依据《企业Java后端开发规范.md》§3.4：Controller 入参使用 SaveReqVO/PageReqVO， 不直接接收 DO。
 *
 * @author bootstrap-project
 */
@Data
@Schema(description = "模型连通性测试请求")
public class ModelPingReqVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "模型 ID（或 provider:modelId）", example = "deepseek-chat", required = true)
  @NotBlank(message = "modelId 不能为空")
  private String modelId;

  @Schema(description = "测试提示词", example = "你好，请用一句话介绍你自己", required = true)
  @NotBlank(message = "prompt 不能为空")
  @Size(max = 2000, message = "prompt 长度不能超过 2000")
  private String prompt;
}
