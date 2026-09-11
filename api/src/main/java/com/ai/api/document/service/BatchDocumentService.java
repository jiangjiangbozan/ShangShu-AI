package com.ai.api.document.service;

import com.ai.api.data.config.RabbitMqConfig;
import com.ai.api.document.dto.BatchDeleteRespDTO;
import com.ai.api.document.dto.BatchProgressRespDTO;
import com.ai.api.document.dto.BatchReprocessRespDTO;
import com.ai.api.knowledge.service.KnowledgeBaseService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 文档批量操作 Service（add-p3-rag-feedback-ops）。
 *
 * <p>批量删除：同步事务删除 MySQL + ES + 物理文件，返回 {@code successCount + failures}。
 *
 * <p>批量重处理：异步投递到 {@code document.batch.task} 队列，立即返回 {@code batchId}， 前端轮询进度接口。Redis SET 去重防止同一
 * docId 重复入队。
 *
 * <p>进度状态存 Redis Hash {@code batch:progress:{batchId}}，TTL 1 天。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Service
public class BatchDocumentService {

  private static final String PROGRESS_KEY_PREFIX = "batch:progress:";
  private static final String DEDUPE_SET_PREFIX = "batch:reprocess:dedupe:";
  private static final Duration PROGRESS_TTL = Duration.ofDays(1);

  /** Redis Hash 字段名。 */
  private static final String FIELD_TOTAL = "total";

  private static final String FIELD_SUCCESS = "success";
  private static final String FIELD_FAILED = "failed";
  private static final String FIELD_PROCESSING = "processing";
  private static final String FIELD_PENDING = "pending";

  private final KnowledgeBaseService knowledgeBaseService;
  private final RabbitTemplate rabbitTemplate;
  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public BatchDocumentService(
      KnowledgeBaseService knowledgeBaseService,
      RabbitTemplate rabbitTemplate,
      RedisTemplate<String, Object> redisTemplate) {
    this.knowledgeBaseService = knowledgeBaseService;
    this.rabbitTemplate = rabbitTemplate;
    this.redisTemplate = redisTemplate;
  }

  /**
   * 批量删除文档（同步事务，部分失败不影响已成功项）。
   *
   * @param docIds 文档 ID 列表
   * @return 成功删除数量与失败详情
   */
  public BatchDeleteRespDTO batchDelete(List<Long> docIds) {
    BatchDeleteRespDTO resp = new BatchDeleteRespDTO();
    if (docIds == null || docIds.isEmpty()) {
      resp.setSuccessCount(0);
      resp.setFailures(List.of());
      return resp;
    }
    int success = 0;
    List<BatchDeleteRespDTO.Failure> failures = new ArrayList<>();
    for (Long docId : docIds) {
      try {
        knowledgeBaseService.deleteDocument(docId);
        success++;
      } catch (Exception e) {
        BatchDeleteRespDTO.Failure f = new BatchDeleteRespDTO.Failure();
        f.setDocId(docId);
        f.setReason(e.getMessage());
        failures.add(f);
        log.warn("[batchDelete][docId={} failed: {}]", docId, e.getMessage());
      }
    }
    resp.setSuccessCount(success);
    resp.setFailures(failures);
    return resp;
  }

  /**
   * 批量重处理文档（异步队列处理）。
   *
   * @param docIds 文档 ID 列表
   * @return 批次 ID 与入队情况
   */
  public BatchReprocessRespDTO batchReprocess(List<Long> docIds) {
    BatchReprocessRespDTO resp = new BatchReprocessRespDTO();
    if (docIds == null || docIds.isEmpty()) {
      resp.setBatchId(null);
      resp.setQueuedCount(0);
      resp.setSkippedDocIds(List.of());
      return resp;
    }
    String batchId = UUID.randomUUID().toString().replace("-", "");
    String dedupeKey = DEDUPE_SET_PREFIX + batchId;

    // 去重：同一 docId 在本批次内只入队一次（Redis SET）
    List<Long> queued = new ArrayList<>();
    List<Long> skipped = new ArrayList<>();
    for (Long docId : docIds) {
      // Redis SET add 返回新增条数：1 表示首次添加，0 表示已存在
      Long added = redisTemplate.opsForSet().add(dedupeKey, docId);
      if (added != null && added > 0) {
        queued.add(docId);
      } else {
        skipped.add(docId);
      }
    }
    // dedupe SET 留 1 天，便于重复提交同一 docId 时跨批次也跳过
    redisTemplate.expire(dedupeKey, PROGRESS_TTL);

    if (queued.isEmpty()) {
      resp.setBatchId(batchId);
      resp.setQueuedCount(0);
      resp.setSkippedDocIds(skipped);
      resp.setProgressUrl("/api/v1/documents/batch/reprocess/status/" + batchId);
      return resp;
    }

    // 初始化进度 Hash
    String progressKey = PROGRESS_KEY_PREFIX + batchId;
    Map<String, Object> progress = new HashMap<>();
    progress.put(FIELD_TOTAL, queued.size());
    progress.put(FIELD_SUCCESS, 0);
    progress.put(FIELD_FAILED, 0);
    progress.put(FIELD_PROCESSING, 0);
    progress.put(FIELD_PENDING, queued.size());
    redisTemplate.opsForHash().putAll(progressKey, progress);
    redisTemplate.expire(progressKey, PROGRESS_TTL);

    // 逐条投递 MQ 消息（每条消息包含 batchId + docId）
    for (Long docId : queued) {
      String payload = buildPayload(batchId, docId);
      rabbitTemplate.convertAndSend(
          RabbitMqConfig.DOCUMENT_BATCH_EXCHANGE,
          RabbitMqConfig.DOCUMENT_BATCH_ROUTING_KEY,
          payload);
    }
    log.info(
        "[batchReprocess][batchId={}, queued={}, skipped={}]",
        batchId,
        queued.size(),
        skipped.size());

    resp.setBatchId(batchId);
    resp.setQueuedCount(queued.size());
    resp.setSkippedDocIds(skipped);
    resp.setProgressUrl("/api/v1/documents/batch/reprocess/status/" + batchId);
    return resp;
  }

  /**
   * 查询批量重处理进度。
   *
   * @param batchId 批次 ID
   * @return 进度状态；不存在返回 null
   */
  public BatchProgressRespDTO getBatchProgress(String batchId) {
    String progressKey = PROGRESS_KEY_PREFIX + batchId;
    Map<Object, Object> entries = redisTemplate.opsForHash().entries(progressKey);
    if (entries.isEmpty()) {
      return null;
    }
    BatchProgressRespDTO resp = new BatchProgressRespDTO();
    resp.setBatchId(batchId);
    resp.setTotal(intOf(entries.get(FIELD_TOTAL)));
    resp.setSuccess(intOf(entries.get(FIELD_SUCCESS)));
    resp.setFailed(intOf(entries.get(FIELD_FAILED)));
    resp.setProcessing(intOf(entries.get(FIELD_PROCESSING)));
    resp.setPending(intOf(entries.get(FIELD_PENDING)));
    resp.setDone(resp.getSuccess() + resp.getFailed() >= resp.getTotal() && resp.getTotal() > 0);
    // 失败详情存于 Hash 字段 failures（JSON 数组），存在时反序列化
    Object failuresRaw = entries.get("failures");
    if (failuresRaw instanceof String s && !s.isBlank()) {
      try {
        resp.setFailures(
            objectMapper.readValue(
                s,
                objectMapper
                    .getTypeFactory()
                    .constructCollectionType(List.class, BatchDeleteRespDTO.Failure.class)));
      } catch (Exception e) {
        log.warn("[getBatchProgress][反序列化失败列表失败: batchId={}, error={}]", batchId, e.getMessage());
      }
    }
    return resp;
  }

  // ============ Consumer 端调用的内部方法 ============

  /**
   * 标记 docId 开始处理（processing +1, pending -1）。
   *
   * <p>仅 {@link com.ai.api.document.mq.BatchDocumentConsumer} 调用。
   */
  public void markProcessing(String batchId, Long docId) {
    String key = PROGRESS_KEY_PREFIX + batchId;
    redisTemplate.opsForHash().increment(key, FIELD_PROCESSING, 1);
    redisTemplate.opsForHash().increment(key, FIELD_PENDING, -1);
  }

  /** 标记 docId 处理成功（success +1, processing -1）。 */
  public void markSuccess(String batchId, Long docId) {
    String key = PROGRESS_KEY_PREFIX + batchId;
    redisTemplate.opsForHash().increment(key, FIELD_SUCCESS, 1);
    redisTemplate.opsForHash().increment(key, FIELD_PROCESSING, -1);
    redisTemplate.opsForSet().remove(DEDUPE_SET_PREFIX + batchId, docId);
  }

  /** 标记 docId 处理失败（failed +1, processing -1），并追加失败详情。 */
  public void markFailed(String batchId, Long docId, String reason) {
    String key = PROGRESS_KEY_PREFIX + batchId;
    redisTemplate.opsForHash().increment(key, FIELD_FAILED, 1);
    redisTemplate.opsForHash().increment(key, FIELD_PROCESSING, -1);
    redisTemplate.opsForSet().remove(DEDUPE_SET_PREFIX + batchId, docId);
    appendFailure(key, docId, reason);
  }

  /** 追加失败项到 Hash 的 failures 字段（JSON 数组）。 */
  private void appendFailure(String key, Long docId, String reason) {
    try {
      BatchDeleteRespDTO.Failure f = new BatchDeleteRespDTO.Failure();
      f.setDocId(docId);
      f.setReason(reason);
      Object existing = redisTemplate.opsForHash().get(key, "failures");
      List<BatchDeleteRespDTO.Failure> list;
      if (existing instanceof String s && !s.isBlank()) {
        list =
            objectMapper.readValue(
                s,
                objectMapper
                    .getTypeFactory()
                    .constructCollectionType(List.class, BatchDeleteRespDTO.Failure.class));
      } else {
        list = new ArrayList<>();
      }
      list.add(f);
      redisTemplate.opsForHash().put(key, "failures", objectMapper.writeValueAsString(list));
    } catch (Exception e) {
      log.warn("[appendFailure][写入失败项异常: key={}, docId={}, error={}]", key, docId, e.getMessage());
    }
  }

  /** 构造 MQ 消息体 JSON（Jackson 标准序列化，避免手写转义问题）。 */
  private String buildPayload(String batchId, Long docId) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("batchId", batchId);
    node.put("docId", docId);
    return node.toString();
  }

  private int intOf(Object v) {
    if (v == null) return 0;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
