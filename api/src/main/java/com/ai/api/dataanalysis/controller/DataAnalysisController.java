package com.ai.api.dataanalysis.controller;

import com.ai.api.common.result.Result;
import com.ai.api.dataanalysis.dto.DataAskRequestDTO;
import com.ai.api.dataanalysis.dto.DataAskResponseDTO;
import com.ai.api.dataanalysis.dto.DataReportRequestDTO;
import com.ai.api.dataanalysis.dto.DataSchemaResponseDTO;
import com.ai.api.dataanalysis.service.DataSchemaCatalog;
import com.ai.api.dataanalysis.service.Nl2SqlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据分析控制器（NL2SQL + 可视化 + 报告导出）。
 *
 * @author add-data-analysis
 */
@Tag(name = "数据分析")
@RestController
@RequestMapping("/api/v1/data-analysis")
@RequiredArgsConstructor
public class DataAnalysisController {

  private final Nl2SqlService nl2SqlService;
  private final DataSchemaCatalog schemaCatalog;

  @Operation(summary = "获取可分析的数据表清单")
  @GetMapping("/schema")
  public Result<DataSchemaResponseDTO> schema() {
    DataSchemaResponseDTO dto = new DataSchemaResponseDTO();
    dto.setTables(schemaCatalog.listTables());
    return Result.success(dto);
  }

  @Operation(summary = "自然语言问数（NL2SQL）")
  @PostMapping("/ask")
  public Result<DataAskResponseDTO> ask(@RequestBody DataAskRequestDTO request) {
    return Result.success(nl2SqlService.ask(request));
  }

  @Operation(summary = "基于问数结果生成 Markdown 分析报告")
  @PostMapping("/report")
  public Result<String> report(@RequestBody DataReportRequestDTO request) {
    return Result.success(nl2SqlService.report(request));
  }
}
