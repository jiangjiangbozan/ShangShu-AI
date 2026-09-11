package com.ai.api.model.controller;

import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.data.entity.SysLlmLog;
import com.ai.api.data.mapper.SysLlmLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** LLM 调用日志查询 Controller。 */
@Tag(name = "LLM 日志")
@RestController
@RequestMapping("/api/v1/llm-logs")
@RequiredArgsConstructor
public class LlmLogController {

  private final SysLlmLogMapper llmLogMapper;

  @Operation(summary = "分页查询 LLM 调用日志")
  @GetMapping
  public Result<PageResult<SysLlmLog>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String modelId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {
    LambdaQueryWrapper<SysLlmLog> wrapper = new LambdaQueryWrapper<>();
    if (modelId != null && !modelId.isBlank()) {
      wrapper.eq(SysLlmLog::getModelId, modelId);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(SysLlmLog::getStatus, status);
    }
    if (traceId != null && !traceId.isBlank()) {
      wrapper.eq(SysLlmLog::getTraceId, traceId);
    }
    if (startDate != null && !startDate.isBlank()) {
      wrapper.ge(SysLlmLog::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
    }
    if (endDate != null && !endDate.isBlank()) {
      wrapper.le(SysLlmLog::getCreateTime, LocalDate.parse(endDate).atTime(LocalTime.MAX));
    }
    wrapper.orderByDesc(SysLlmLog::getCreateTime);
    IPage<SysLlmLog> result = llmLogMapper.selectPage(new Page<>(page, size), wrapper);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }

  @Operation(summary = "按 traceId 查询调用链路日志")
  @GetMapping("/trace/{traceId}")
  public Result<java.util.List<SysLlmLog>> getByTraceId(@RequestParam String traceId) {
    return Result.success(llmLogMapper.selectByTraceId(traceId));
  }

  @Operation(summary = "查询 LLM 日志聚合统计")
  @GetMapping("/stats")
  public Result<java.util.Map<String, Object>> stats(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {
    LocalDateTime start =
        startDate != null && !startDate.isBlank()
            ? LocalDate.parse(startDate).atStartOfDay()
            : LocalDate.now().minusDays(7).atStartOfDay();
    LocalDateTime end =
        endDate != null && !endDate.isBlank()
            ? LocalDate.parse(endDate).atTime(LocalTime.MAX)
            : LocalDateTime.now();

    LambdaQueryWrapper<SysLlmLog> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(SysLlmLog::getCreateTime, start).le(SysLlmLog::getCreateTime, end);

    Long total = llmLogMapper.selectCount(wrapper);

    LambdaQueryWrapper<SysLlmLog> successWrapper = wrapper.clone();
    successWrapper.eq(SysLlmLog::getStatus, "success");
    Long success = llmLogMapper.selectCount(successWrapper);

    LambdaQueryWrapper<SysLlmLog> failedWrapper = wrapper.clone();
    failedWrapper.eq(SysLlmLog::getStatus, "failed");
    Long failed = llmLogMapper.selectCount(failedWrapper);

    // 计算 Token 总量
    wrapper.select(SysLlmLog::getTotalTokens);
    java.util.List<SysLlmLog> allLogs = llmLogMapper.selectList(wrapper);
    long totalTokens =
        allLogs.stream().mapToLong(l -> l.getTotalTokens() != null ? l.getTotalTokens() : 0).sum();
    long avgLatency =
        allLogs.isEmpty()
            ? 0
            : (long)
                allLogs.stream()
                    .mapToLong(l -> l.getLatencyMs() != null ? l.getLatencyMs() : 0)
                    .average()
                    .orElse(0);

    return Result.success(
        java.util.Map.of(
            "total", total,
            "success", success,
            "failed", failed,
            "totalTokens", totalTokens,
            "avgLatencyMs", avgLatency));
  }
}
