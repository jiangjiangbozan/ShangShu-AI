package com.ai.api.knowledge.controller;

import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.knowledge.entity.ChunkFeedbackLog;
import com.ai.api.knowledge.mapper.ChunkFeedbackLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 知识库反馈日志查询 Controller。 */
@Tag(name = "知识库反馈")
@RestController
@RequestMapping("/api/v1/knowledge/feedback")
@RequiredArgsConstructor
public class ChunkFeedbackController {

  private final ChunkFeedbackLogMapper feedbackLogMapper;

  @Operation(summary = "分页查询反馈日志")
  @GetMapping
  public Result<PageResult<ChunkFeedbackLog>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Long chunkId,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String feedbackType) {
    LambdaQueryWrapper<ChunkFeedbackLog> wrapper = new LambdaQueryWrapper<>();
    if (chunkId != null) {
      wrapper.eq(ChunkFeedbackLog::getChunkId, chunkId);
    }
    if (userId != null) {
      wrapper.eq(ChunkFeedbackLog::getUserId, userId);
    }
    if (feedbackType != null && !feedbackType.isBlank()) {
      wrapper.eq(ChunkFeedbackLog::getFeedbackType, feedbackType);
    }
    wrapper.orderByDesc(ChunkFeedbackLog::getCreateTime);
    IPage<ChunkFeedbackLog> result = feedbackLogMapper.selectPage(new Page<>(page, size), wrapper);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }
}
