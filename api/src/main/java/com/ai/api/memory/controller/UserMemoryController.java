package com.ai.api.memory.controller;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import com.ai.api.memory.dto.MemoryCreateReqDTO;
import com.ai.api.memory.dto.MemoryRespDTO;
import com.ai.api.memory.service.UserMemoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户长期记忆 API
 *
 * <p>提供记忆的 CRUD 和搜索功能，用于在对话中注入用户上下文。 借鉴 Operit 的本地记忆库设计，走服务端持久化路线。
 */
@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
public class UserMemoryController {

  private final UserMemoryService memoryService;

  /** 创建记忆 */
  @PostMapping
  public Result<MemoryRespDTO> create(@Valid @RequestBody MemoryCreateReqDTO req) {
    Long userId = getCurrentUserId();
    return Result.success(memoryService.createMemory(userId, req));
  }

  /** 查询记忆列表 */
  @GetMapping
  public Result<List<MemoryRespDTO>> list(
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    Long userId = getCurrentUserId();
    return Result.success(memoryService.listMemories(userId, category, page, Math.min(size, 50)));
  }

  /** 搜索记忆 */
  @GetMapping("/search")
  public Result<List<MemoryRespDTO>> search(
      @RequestParam String keyword, @RequestParam(defaultValue = "20") int size) {
    Long userId = getCurrentUserId();
    return Result.success(memoryService.searchMemories(userId, keyword, Math.min(size, 50)));
  }

  /** 删除记忆 */
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    Long userId = getCurrentUserId();
    memoryService.deleteMemory(userId, id);
    return Result.success(null);
  }

  /** 获取上下文记忆（用于注入 LLM 上下文） */
  @GetMapping("/context")
  public Result<List<MemoryRespDTO>> context(@RequestParam(defaultValue = "10") int limit) {
    Long userId = getCurrentUserId();
    return Result.success(memoryService.getContextMemories(userId, Math.min(limit, 20)));
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails details)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }
    return details.userId();
  }
}
