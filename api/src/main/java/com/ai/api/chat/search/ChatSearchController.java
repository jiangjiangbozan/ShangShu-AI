package com.ai.api.chat.search;

import com.ai.api.common.result.Result;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话历史全局检索 REST API。
 *
 * <p>GET /api/v1/chat/search?q=xxx
 *
 * <p>支持角色筛选（role=user/assistant）和分页（hasMore）。
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatSearchController {

  private final ChatSearchService chatSearchService;
  private final ChatSearchReindexService chatSearchReindexService;

  public ChatSearchController(
      ChatSearchService chatSearchService, ChatSearchReindexService chatSearchReindexService) {
    this.chatSearchService = chatSearchService;
    this.chatSearchReindexService = chatSearchReindexService;
  }

  /**
   * 存量消息回填 ES 索引。
   *
   * <p>POST /api/v1/chat/search/reindex
   *
   * <p>把 MySQL chat_message 全量消息（user + assistant）同步到 ES，幂等（按主键覆盖）。
   *
   * @return 已回填的消息条数
   */
  @PostMapping("/search/reindex")
  public Result<Integer> reindex() {
    return Result.success(chatSearchReindexService.reindexAll());
  }

  /**
   * 全局检索对话历史。
   *
   * <p>GET /api/v1/chat/search?q=xxx&page=1&size=20&userId=&sessionId=&role=&startTime=&endTime=
   *
   * @param query 搜索关键词（必填）
   * @param page 页码（从 1 开始，默认 1）
   * @param size 每页条数（默认 20）
   * @param userId 限定用户 ID（可选）
   * @param sessionId 限定会话 ID（可选）
   * @param role 限定角色 user/assistant（可选，为空则搜索全部角色）
   * @param startTime 起始时间毫秒时间戳（可选）
   * @param endTime 截止时间毫秒时间戳（可选）
   * @return 搜索结果（total/hits/page/size/hasMore）
   */
  @GetMapping("/search")
  public Result<Map<String, Object>> search(
      @RequestParam("q") String query,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Long sessionId,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) Long startTime,
      @RequestParam(required = false) Long endTime) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("查询关键词 q 不能为空");
    }
    // 校验 role 参数合法性
    if (role != null && !role.isBlank() && !"user".equals(role) && !"assistant".equals(role)) {
      throw new IllegalArgumentException("role 参数仅支持 user 或 assistant");
    }
    return Result.success(
        chatSearchService.search(query, page, size, userId, sessionId, role, startTime, endTime));
  }
}
