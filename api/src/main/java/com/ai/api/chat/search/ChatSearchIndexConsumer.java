package com.ai.api.chat.search;

import com.ai.api.data.config.RabbitMqConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 对话历史 ES 索引任务消费者（add-p2-cache-search-rewrite）。
 *
 * <p>监听 {@code chat.search.index} 队列，将消息索引到 ES {@code chat_message_log} 索引。
 *
 * <p>手动 ack + 死信队列：失败 nack 入死信队列，避免无限重投。 JSON 解析失败直接 ack 丢弃。
 *
 * @author add-p2-cache-search-rewrite
 */
@Slf4j
@Component
public class ChatSearchIndexConsumer {

  private final ElasticsearchOperations esOperations;
  private final ChatSearchIndexInitializer indexInitializer;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ChatSearchIndexConsumer(
      ElasticsearchOperations esOperations, ChatSearchIndexInitializer indexInitializer) {
    this.esOperations = esOperations;
    this.indexInitializer = indexInitializer;
  }

  @RabbitListener(queues = RabbitMqConfig.CHAT_SEARCH_INDEX_QUEUE)
  public void handleIndexTask(
      String payload, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag, Channel channel)
      throws IOException {
    try {
      JsonNode node = objectMapper.readTree(payload);
      String messageId = node.get("messageId").asText();
      // 用 source(JSON) 方式写入，避免 IndexQueryBuilder 无 withDocument 方法
      String source = buildSource(node);
      IndexQuery query = new IndexQueryBuilder().withId(messageId).withSource(source).build();
      esOperations.index(query, indexInitializer.getIndexCoordinates());
      log.debug("[ChatSearchIndexConsumer][索引成功: messageId={}]", messageId);
      channel.basicAck(deliveryTag, false);
    } catch (JacksonException je) {
      log.error(
          "[ChatSearchIndexConsumer][JSON 解析失败，丢弃: payload={}]",
          payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);
      channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
      log.error("[ChatSearchIndexConsumer][索引失败，nack 入死信: error={}]", e.getMessage(), e);
      channel.basicNack(deliveryTag, false, false);
    }
  }

  /**
   * 直接索引单条消息到 ES（供存量回填复用）。
   *
   * <p>与 MQ 链路写入完全一致：以消息主键作为 ES 文档 _id，重复回填时按 id 覆盖幂等。
   *
   * @param messageId 消息主键（ES 文档 _id）
   * @param sessionId 会话 ID
   * @param userId 用户 ID（可为 null，写入 0）
   * @param role 角色 user/assistant
   * @param content 消息内容
   * @param title 会话标题（可为 null）
   * @param modelId 模型 ID（可为 null）
   * @param createTime 消息创建时间（epoch_millis）
   */
  public void indexMessage(
      String messageId,
      Long sessionId,
      Long userId,
      String role,
      String content,
      String title,
      String modelId,
      long createTime) {
    ObjectNode src = objectMapper.createObjectNode();
    src.put("messageId", messageId);
    src.put("sessionId", sessionId);
    src.put("userId", userId == null ? 0L : userId);
    src.put("role", role);
    src.put("content", content);
    src.put("title", title == null ? "" : title);
    src.put("modelId", modelId == null ? "" : modelId);
    src.put("createTime", createTime);
    IndexQuery query = new IndexQueryBuilder().withId(messageId).withSource(src.toString()).build();
    esOperations.index(query, indexInitializer.getIndexCoordinates());
  }

  private String buildSource(JsonNode node) {
    ObjectNode src = objectMapper.createObjectNode();
    src.put("messageId", node.get("messageId").asText());
    src.put("sessionId", node.get("sessionId").asLong());
    if (node.has("userId")) {
      src.put("userId", node.get("userId").asLong());
    } else {
      src.put("userId", 0L);
    }
    src.put("role", node.get("role").asText());
    src.put("content", node.get("content").asText());
    // 会话标题（可选，用于搜索匹配会话标题关键词）
    if (node.has("title") && !node.get("title").isNull()) {
      src.put("title", node.get("title").asText());
    } else {
      src.put("title", "");
    }
    if (node.has("modelId")) {
      src.put("modelId", node.get("modelId").asText());
    } else {
      src.put("modelId", "");
    }
    src.put("createTime", System.currentTimeMillis());
    return src.toString();
  }
}
