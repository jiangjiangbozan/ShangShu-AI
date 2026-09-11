package com.ai.api.dataanalysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据分析提问请求（NL2SQL）。
 *
 * <p>用户以自然语言提问，后端基于平台数据表生成并执行 SQL，返回结果集与图表规格。
 *
 * @author add-data-analysis
 */
@Data
@Schema(description = "数据分析提问请求")
public class DataAskRequestDTO {

  @Schema(description = "自然语言问题，如：近7天每天新增多少会话", example = "近7天每天新增多少会话")
  @NotBlank(message = "问题不能为空")
  private String question;

  @Schema(description = "可选：使用的模型 ID，默认 deepseek-chat", example = "deepseek-chat")
  private String modelId;
}
