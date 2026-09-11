package com.ai.api.chat.search;

import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.entity.ChatSession;
import com.ai.api.chat.mapper.ChatMessageMapper;
import com.ai.api.chat.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对话历史 ES 存量回填 Service。
 *
 * <p>把 MySQL {@code chat_message} 全量消息（含 user 与 assistant）同步写入 ES {@code chat_message_log} 索引。 供修复
 * ES 双写短路之后，一次性把历史消息补进索引，让全局检索能命中存量数据。
 *
 * <p>幂等：ES 文档 _id 取消息主键 id，重复回填时按 id 覆盖，不会产生重复文档。
 *
 * @author add-p2-cache-search-rewrite
 */
@Slf4j
@Service
public class ChatSearchReindexService {

  private final ChatMessageMapper messageMapper;
  private final ChatSessionMapper sessionMapper;
  private final ChatSearchIndexConsumer indexConsumer;

  public ChatSearchReindexService(
      ChatMessageMapper messageMapper,
      ChatSessionMapper sessionMapper,
      ChatSearchIndexConsumer indexConsumer) {
    this.messageMapper = messageMapper;
    this.sessionMapper = sessionMapper;
    this.indexConsumer = indexConsumer;
  }

  /**
   * 全量回填：分页扫描 MySQL 消息并写入 ES。
   *
   * @return 已回填的消息条数
   */
  public int reindexAll() {
    // 预加载全部会话（id -> session），用于补 userId 与 title
    Map<Long, ChatSession> sessionMap =
        sessionMapper.selectList(null).stream()
            .collect(Collectors.toMap(ChatSession::getId, Function.identity(), (a, b) -> a));

    int pageSize = 200;
    int total = 0;
    long lastId = 0L;
    while (true) {
      List<ChatMessage> page =
          messageMapper.selectList(
              new LambdaQueryWrapper<ChatMessage>()
                  .gt(ChatMessage::getId, lastId)
                  .orderByAsc(ChatMessage::getId)
                  .last("LIMIT " + pageSize));
      if (page.isEmpty()) {
        break;
      }
      for (ChatMessage msg : page) {
        ChatSession session = sessionMap.get(msg.getSessionId());
        Long userId = session != null ? session.getUserId() : null;
        String title = session != null ? session.getTitle() : null;
        long createTime =
            msg.getCreateTime() != null
                ? msg.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : System.currentTimeMillis();
        indexConsumer.indexMessage(
            String.valueOf(msg.getId()),
            msg.getSessionId(),
            userId,
            msg.getRole(),
            msg.getContent(),
            title,
            msg.getModelId(),
            createTime);
        total++;
      }
      lastId = page.get(page.size() - 1).getId();
      if (page.size() < pageSize) {
        break;
      }
    }
    log.info("[ChatSearchReindexService][存量回填完成: total={}]", total);
    return total;
  }
}
