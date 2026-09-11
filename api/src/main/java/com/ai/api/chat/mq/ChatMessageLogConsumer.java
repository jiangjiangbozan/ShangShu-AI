package com.ai.api.chat.mq;

import com.ai.api.chat.cache.ChatHistoryCacheService;
import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.mapper.ChatMessageMapper;
import com.ai.api.data.config.RabbitMqConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 聊天消息日志消费者
 *
 * <p>消费 {@code chat.message.log} 队列中的消息，异步写入 MySQL。
 *
 * <p>手动 ack（acknowledge-mode: manual）：
 *
 * <ul>
 *   <li>处理成功：{@code basicAck} 单条确认
 *   <li>业务异常：{@code basicNack} 入死信队列，避免无限重试
 *   <li>消息反序列化失败：由全局 JacksonJsonMessageConverter 抛出，不进入方法体（由 Spring AMQP 默认错误处理）
 * </ul>
 *
 * <p>幂等设计：用 {@code chat_message.mq_msg_id} UNIQUE 索引去重。 RabbitMQ retry 重复投递时 INSERT 触发
 * DuplicateKeyException 被捕获并视作成功， 避免重复入库。{@code mq_msg_id} 来源：
 *
 * <ol>
 *   <li>消息体 {@code mqMsgId} 字段（由 Producer 写入）
 *   <li>fallback 到 RabbitMQ 消息 properties.messageId
 * </ol>
 *
 * <p>缓存失效（add-p2-cache-search-rewrite）：写 MySQL 成功后调用 {@code chatHistoryCacheService.evict} 失效 Redis
 * 会话历史缓存，保证一致性。
 *
 * <p>ES 双写（add-p2-cache-search-rewrite）：写 MySQL 成功后向 {@code chat.search.index} 队列发送 ES 索引任务， 由
 * {@link com.ai.api.chat.search.ChatSearchIndexConsumer} 异步索引到 ES {@code chat_message_log} 索引。ES
 * 索引失败仅 nack 入死信，不阻塞主流程。
 *
 * @author add-chat-module
 */
@Slf4j
@Component
public class ChatMessageLogConsumer {

  private final ChatMessageMapper messageMapper;
  private final ChatHistoryCacheService chatHistoryCacheService;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ChatMessageLogConsumer(
      ChatMessageMapper messageMapper,
      ChatHistoryCacheService chatHistoryCacheService,
      RabbitTemplate rabbitTemplate) {
    this.messageMapper = messageMapper;
    this.chatHistoryCacheService = chatHistoryCacheService;
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * 监听 chat.message.log 队列，写入消息日志。
   *
   * @param payload 消息体（POJO，由全局 JacksonJsonMessageConverter 自动反序列化）
   * @param deliveryTag RabbitMQ 投递标识，用于 ack/nack
   * @param messageId RabbitMQ 消息 ID（fallback 幂等键）
   * @param channel RabbitMQ Channel（手动 ack 用）
   */
  @RabbitListener(queues = RabbitMqConfig.CHAT_LOG_QUEUE)
  public void handleMessageLog(
      @Payload ChatMessageLogMessage payload,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
      @Header(name = "amqp_messageId", required = false) String messageId,
      Channel channel)
      throws IOException {
    String mqMsgId = null;
    try {
      // 幂等键优先取消息体，fallback 到 RabbitMQ messageId
      if (payload.getMqMsgId() != null && !payload.getMqMsgId().isBlank()) {
        mqMsgId = payload.getMqMsgId();
      } else if (messageId != null && !messageId.isBlank()) {
        mqMsgId = messageId;
      }

      ChatMessage message = new ChatMessage();
      message.setSessionId(payload.getSessionId());
      message.setRole(payload.getRole());
      message.setContent(payload.getContent());
      message.setModelId(payload.getModelId());
      message.setTotalTokens(payload.getTotalTokens());
      message.setLatencyMs(payload.getLatencyMs());
      message.setMqMsgId(mqMsgId);

      try {
        messageMapper.insert(message);
        // 写 MySQL 成功后失效会话历史缓存（Cache-Aside），下次读取触发回源回填
        chatHistoryCacheService.evict(message.getSessionId());
        // ES 双写：发送索引任务到 chat.search.index 队列，ES 失败不影响主流程
        publishEsIndexTask(payload, message);
      } catch (DuplicateKeyException dupEx) {
        // 幂等命中：mq_msg_id UNIQUE 索引重复。可能来源：
        //   1. RabbitMQ retry 重复投递；
        //   2. ChatStreamService 已直存 MySQL（携带 mqMsgId）后再发本队列——此时 insert 必命中幂等。
        // 关键：幂等命中不等于"跳过 ES 双写"。此前仅 ack 丢弃导致 ES 双写被幂等短路，
        // 新消息永远无法被全局检索。此处必须反查已入库记录并继续发布 ES 索引任务。
        ChatMessage existing =
            messageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getMqMsgId, mqMsgId));
        if (existing != null && existing.getId() != null) {
          // 复用已入库记录的主键作为 ES 文档 _id，保证 ES 侧幂等覆盖
          message.setId(existing.getId());
          log.info(
              "[ChatMessageLogConsumer][幂等命中，复用已入库记录并发布 ES 索引任务: messageId={}, mqMsgId={}]",
              existing.getId(),
              mqMsgId);
          publishEsIndexTask(payload, message);
        } else {
          log.warn("[ChatMessageLogConsumer][幂等命中但反查无记录，跳过 ES 双写: mqMsgId={}]", mqMsgId);
        }
      }
      // 成功 ack（单条，不批量）
      channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
      log.error(
          "[ChatMessageLogConsumer][处理消息日志失败，nack 入死信: mqMsgId={}, error={}]",
          mqMsgId,
          e.getMessage(),
          e);
      // requeue=false：直接进死信队列，避免无限重投
      channel.basicNack(deliveryTag, false, false);
    }
  }

  /**
   * 构造 ES 索引任务消息并发送到 {@code chat.search.index} 队列（add-p2-cache-search-rewrite）。
   *
   * <p>messageId 取 INSERT 后回填的主键；userId 若消息体携带则传递。
   */
  private void publishEsIndexTask(ChatMessageLogMessage source, ChatMessage message) {
    try {
      ObjectNode task = objectMapper.createObjectNode();
      task.put("messageId", String.valueOf(message.getId()));
      task.put("sessionId", message.getSessionId());
      if (source.getUserId() != null) {
        task.put("userId", source.getUserId());
      }
      task.put("role", message.getRole());
      task.put("content", message.getContent());
      // 会话标题：用于 ES 搜索匹配标题关键词
      if (source.getTitle() != null && !source.getTitle().isBlank()) {
        task.put("title", source.getTitle());
      } else {
        task.put("title", "");
      }
      task.put("modelId", message.getModelId());
      rabbitTemplate.convertAndSend(
          RabbitMqConfig.CHAT_SEARCH_INDEX_EXCHANGE,
          RabbitMqConfig.CHAT_SEARCH_INDEX_ROUTING_KEY,
          objectMapper.writeValueAsString(task));
    } catch (Exception e) {
      // 索引任务发送失败仅告警，不影响 MySQL 写入与 ack
      log.warn(
          "[ChatMessageLogConsumer][ES 索引任务发送失败，跳过 ES 双写: messageId={}, error={}]",
          message.getId(),
          e.getMessage());
    }
  }
}
