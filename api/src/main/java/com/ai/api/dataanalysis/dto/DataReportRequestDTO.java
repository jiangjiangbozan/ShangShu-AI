package com.ai.api.dataanalysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 生成分析报告请求（复用一次 ask 的结果）。
 *
 * @author add-data-analysis
 */
@Data
@Schema(description = "生成分析报告请求")
public class DataReportRequestDTO {

  @Schema(description = "原始问题")
  private String question;

  @Schema(description = "执行的 SQL")
  private String sql;

  @Schema(description = "结果集列名")
  private List<String> columns;

  @Schema(description = "结果集行")
  private List<Map<String, Object>> rows;
}
