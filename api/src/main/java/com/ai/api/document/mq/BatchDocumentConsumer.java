package com.ai.api.document.mq;

import com.ai.api.data.config.RabbitMqConfig;
import com.ai.api.document.service.BatchDocumentService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 文档批量重处理任务消费者（add-p3-rag-feedback-ops）。
 *
 * <p>监听 {@code document.batch.task} 队列，调用 {@link
 * com.ai.api.knowledge.service.KnowledgeBaseService#reprocessDocument(Long)} 重新解析+向量化。
 *
 * <p>手动 ack + 死信队列：业务异常 nack 入 DLQ；JSON 解析失败 ack 丢弃避免毒消息。
 *
 * <p>消息体格式（Jackson 标准序列化，由 Producer 构造）：
 *
 * <pre>{@code
 * {"batchId":"<uuid>","docId":<long>}
 * }</pre>
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Component
public class BatchDocumentConsumer {

  private final BatchDocumentService batchDocumentService;
  private final com.ai.api.knowledge.service.KnowledgeBaseService knowledgeBaseService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public BatchDocumentConsumer(
      BatchDocumentService batchDocumentService,
      com.ai.api.knowledge.service.KnowledgeBaseService knowledgeBaseService) {
    this.batchDocumentService = batchDocumentService;
    this.knowledgeBaseService = knowledgeBaseService;
  }

  @RabbitListener(queues = RabbitMqConfig.DOCUMENT_BATCH_QUEUE)
  public void handleBatchTask(
      String payload, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag, Channel channel)
      throws IOException {
    String batchId = null;
    Long docId = null;
    try {
      JsonNode node = objectMapper.readTree(payload);
      batchId = node.get("batchId").asText();
      docId = node.get("docId").asLong();
      log.info("[BatchDocumentConsumer][开始处理: batchId={}, docId={}]", batchId, docId);
      batchDocumentService.markProcessing(batchId, docId);
      knowledgeBaseService.reprocessDocument(docId);
      batchDocumentService.markSuccess(batchId, docId);
      channel.basicAck(deliveryTag, false);
    } catch (JacksonException je) {
      log.error(
          "[BatchDocumentConsumer][JSON 解析失败，丢弃: payload={}]",
          payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);
      channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
      log.error(
          "[BatchDocumentConsumer][重处理失败，nack 入死信: batchId={}, docId={}, error={}]",
          batchId,
          docId,
          e.getMessage(),
          e);
      if (batchId != null && docId != null) {
        try {
          batchDocumentService.markFailed(batchId, docId, e.getMessage());
        } catch (Exception markEx) {
          log.warn("[BatchDocumentConsumer][markFailed 异常（忽略）: error={}]", markEx.getMessage());
        }
      }
      channel.basicNack(deliveryTag, false, false);
    }
  }
}
