package com.ai.api.dataanalysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 数据分析可用数据表清单（NL2SQL 元数据，已剔除敏感列）。
 *
 * @author add-data-analysis
 */
@Data
@Schema(description = "可分析的数据表清单")
public class DataSchemaResponseDTO {

  @Schema(description = "数据表列表")
  private List<TableMeta> tables;

  /** 数据表元信息。 */
  @Data
  public static class TableMeta {
    @Schema(description = "表名")
    private String name;

    @Schema(description = "表注释")
    private String comment;

    @Schema(description = "列列表")
    private List<ColumnMeta> columns;
  }

  /** 列元信息。 */
  @Data
  public static class ColumnMeta {
    @Schema(description = "列名")
    private String name;

    @Schema(description = "列注释")
    private String comment;

    @Schema(description = "列类型：int/string/datetime/decimal/boolean")
    private String type;
  }
}
