package com.ai.api.chat.annotation.controller;

import com.ai.api.chat.annotation.entity.Annotation;
import com.ai.api.chat.annotation.service.AnnotationService;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 标注问答管理 Controller */
@Tag(name = "标注问答管理")
@RestController
@RequestMapping("/api/v1/annotations")
@RequiredArgsConstructor
public class AnnotationController {

  private final AnnotationService annotationService;

  @Operation(summary = "分页查询标注列表")
  @GetMapping
  public Result<PageResult<Annotation>> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<Annotation> result = annotationService.list(keyword, page, size);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }

  @Operation(summary = "新增标注")
  @PostMapping
  public Result<Annotation> create(@RequestBody Map<String, Object> body) {
    String question = (String) body.get("question");
    String answer = (String) body.get("answer");
    Long messageId =
        body.get("messageId") != null ? ((Number) body.get("messageId")).longValue() : null;
    if (question == null || question.isBlank()) return Result.fail("问题不能为空");
    if (answer == null || answer.isBlank()) return Result.fail("回答不能为空");
    return Result.success(annotationService.create(question, answer, messageId));
  }

  @Operation(summary = "编辑标注")
  @PutMapping("/{id}")
  public Result<Annotation> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
    return Result.success(annotationService.update(id, body.get("question"), body.get("answer")));
  }

  @Operation(summary = "删除标注")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    annotationService.delete(id);
    return Result.success();
  }

  @Operation(summary = "批量删除标注")
  @DeleteMapping("/batch")
  public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
    annotationService.deleteBatch(ids);
    return Result.success();
  }

  @Operation(summary = "启用/禁用标注")
  @PutMapping("/{id}/toggle")
  public Result<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
    annotationService.toggleEnabled(id, body.getOrDefault("enabled", true));
    return Result.success();
  }
}
