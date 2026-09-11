package com.ai.api.audit.controller;

import com.ai.api.audit.entity.OperationLog;
import com.ai.api.audit.service.OperationLogService;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作审计日志（A6）。 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final OperationLogService operationLogService;

  public AuditLogController(OperationLogService operationLogService) {
    this.operationLogService = operationLogService;
  }

  /** 分页查询。GET /api/v1/audit-logs */
  @GetMapping
  public Result<PageResult<OperationLog>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
          LocalDateTime start,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
          LocalDateTime end) {
    IPage<OperationLog> result =
        operationLogService.list(page, size, username, module, action, status, start, end);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }

  /** 清理 N 天前的日志。DELETE /api/v1/audit-logs?days=30 */
  @DeleteMapping
  @com.ai.api.audit.annotation.OperationLog(module = "audit", action = "clean")
  public Result<Map<String, Object>> clean(@RequestParam(defaultValue = "30") int days) {
    int count = operationLogService.cleanBefore(days);
    return Result.success(
        Map.of("cleaned", count, "before", LocalDateTime.now().minusDays(days).format(FMT)));
  }
}
