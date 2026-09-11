package com.ai.api.model.service;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型调用测试结果
 *
 * <p>依据 design.md D4：返回模型响应、modelId、延迟、Token 用量。
 *
 * @author bootstrap-project
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型连通性测试结果")
public class ModelPingResult implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "模型响应内容", example = "你好！我是 DeepSeek...")
  private String content;

  @Schema(description = "调用的模型 ID", example = "deepseek-chat")
  private String modelId;

  @Schema(description = "调用耗时（毫秒）", example = "1234")
  private Long latencyMs;

  @Schema(description = "输入 Token 数", example = "10")
  private Integer promptTokens;

  @Schema(description = "输出 Token 数", example = "50")
  private Integer completionTokens;

  @Schema(description = "总 Token 数", example = "60")
  private Integer totalTokens;
}
