package com.ai.api.dataanalysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 数据分析回答（含生成的 SQL、结果集与图表规格）。
 *
 * @author add-data-analysis
 */
@Data
@Schema(description = "数据分析回答")
public class DataAskResponseDTO {

  @Schema(description = "原始问题")
  private String question;

  @Schema(description = "生成的 SQL（已通过安全校验）")
  private String sql;

  @Schema(description = "结果集列名")
  private List<String> columns;

  @Schema(description = "结果集行（列名→值）")
  private List<Map<String, Object>> rows;

  @Schema(description = "图表类型：bar/line/pie/table")
  private String chartType;

  @Schema(description = "图表标题")
  private String chartTitle;

  @Schema(description = "提示信息（如无数据说明）")
  private String message;

  @Schema(description = "总耗时（毫秒）")
  private long latencyMs;
}
