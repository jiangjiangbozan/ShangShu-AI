package com.ai.api.chat.service;

import com.ai.api.chat.dto.WebSearchResult;
import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.entity.ChatSession;
import com.ai.api.chat.mapper.ChatSessionMapper;
import com.ai.api.chat.mq.ChatMessageLogProducer;
import com.ai.api.knowledge.service.KnowledgeBaseService;
import com.ai.api.limit.service.TokenLimitService;
import com.ai.api.model.config.ChatClientFactory;
import com.ai.api.model.route.service.ModelRouteService;
import com.ai.api.moderation.service.ModerationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * SSE 流式响应编排 Service
 *
 * <p>依据 spec.md "消息发送与 SSE 流式响应"：使用 Spring AI {@link ChatClient} + {@link ChatMemory} + {@link
 * Flux} 实现 SSE 流式输出。
 *
 * @author add-chat-module
 */
@Slf4j
@Service
public class ChatStreamService {

  private final ChatClientFactory chatClientFactory;
  private final ChatMemory chatMemory;
  private final ChatSessionMapper sessionMapper;
  private final ChatMessageService messageService;
  private final ChatMessageLogProducer messageLogProducer;
  private final ChatSessionService sessionService;
  private final KnowledgeBaseService knowledgeBaseService;
  private final VectorStore vectorStore;
  private final WebSearchService webSearchService;
  private final TokenLimitService tokenLimitService;
  private final ModelRouteService modelRouteService;
  private final ModerationService moderationService;

  public ChatStreamService(
      ChatClientFactory chatClientFactory,
      ChatMemory chatMemory,
      ChatSessionMapper sessionMapper,
      ChatMessageService messageService,
      ChatMessageLogProducer messageLogProducer,
      ChatSessionService sessionService,
      KnowledgeBaseService knowledgeBaseService,
      @Qualifier("vectorStore") VectorStore vectorStore,
      WebSearchService webSearchService,
      TokenLimitService tokenLimitService,
      ModelRouteService modelRouteService,
      ModerationService moderationService) {
    this.chatClientFactory = chatClientFactory;
    this.chatMemory = chatMemory;
    this.sessionMapper = sessionMapper;
    this.messageService = messageService;
    this.messageLogProducer = messageLogProducer;
    this.sessionService = sessionService;
    this.knowledgeBaseService = knowledgeBaseService;
    this.vectorStore = vectorStore;
    this.webSearchService = webSearchService;
    this.tokenLimitService = tokenLimitService;
    this.modelRouteService = modelRouteService;
    this.moderationService = moderationService;
  }

  /**
   * 发送消息并返回 SSE 流式响应。
   *
   * @param sessionId 会话 ID
   * @param userId 用户 ID
   * @param modelId 模型 ID
   * @param content 用户消息内容
   * @param kbId 知识库 ID（可选，非空则启用 RAG 检索增强）
   * @param webSearch 是否启用网络搜索增强（可选，为 true 时先搜索网络并注入上下文）
   * @return Flux<ServerSentEvent<String>>
   */
  public Flux<ServerSentEvent<String>> streamMessage(
      Long sessionId, Long userId, String modelId, String content, Long kbId, boolean webSearch) {

    // 1. 查找 ChatClient（A5：主模型不可用时按降级策略链路由到第一个可用模型）
    ResolvedModel resolved = resolveModelWithFallback(modelId);
    ChatClient chatClient = resolved.chatClient();
    if (chatClient == null) {
      return Flux.just(
          ServerSentEvent.<String>builder()
              .event("error")
              .data("{\"error\":\"模型 " + modelId + " 及其降级模型均不可用\"}")
              .build());
    }

    // 1.1 Token 限额 / 限流前置检查（A2）：超限时拒绝本次请求，不保存用户消息、不调用 LLM。
    var limitCheck = tokenLimitService.preCheck(userId);
    if (!limitCheck.allowed()) {
      log.warn("[streamMessage][userId({}) rejected by limit: {}]", userId, limitCheck.reason());
      return Flux.just(
          ServerSentEvent.<String>builder()
              .event("error")
              .data("{\"error\":\"" + limitCheck.reason() + "\"}")
              .build());
    }

    // 1.2 内容审查前置检查（A4）：命中敏感内容时拒绝本次请求，不保存用户消息、不调用 LLM。
    var moderation = moderationService.checkContent(content);
    if (moderation.flagged()) {
      log.warn(
          "[streamMessage][userId({}) rejected by moderation: {}]", userId, moderation.reason());
      return Flux.just(
          ServerSentEvent.<String>builder()
              .event("error")
              .data("{\"error\":\"" + moderation.reason() + "\"}")
              .build());
    }

    // 2. 保存用户消息
    // 幂等键：用户消息直存 MySQL 时即带上独立 mqMsgId，随后 sendLog 复用同一 UUID，
    // Consumer 端 insert 触发 DuplicateKeyException 幂等命中（不重复入库），
    // 同时经修复后的 Consumer 发布 ES 索引任务，保证用户提问内容可被全局检索。
    String userMqMsgId = UUID.randomUUID().toString();
    ChatMessage userMessage = new ChatMessage();
    userMessage.setSessionId(sessionId);
    userMessage.setRole("user");
    userMessage.setContent(content);
    userMessage.setModelId(modelId);
    userMessage.setMqMsgId(userMqMsgId);
    messageService.saveMessage(userMessage);
    // 用户消息也进入 ES 索引链路（role=user）；标题从会话表取，取不到则传 null
    ChatSession userMsgSession = sessionMapper.selectById(sessionId);
    messageLogProducer.sendLog(
        sessionId,
        userId,
        "user",
        content,
        modelId,
        0,
        0,
        userMqMsgId,
        userMsgSession != null ? userMsgSession.getTitle() : null);

    // 3. 构造 ChatMemory Advisor
    // 关键：必须把 ChatMemory 通过 Advisor 接入 ChatClient 调用链，模型才能拿到历史上下文。
    // 之前只调用 chatMemory.add() 把消息写入 Redis，但 chatClient.prompt().user(content)
    // 没有挂载 advisor，模型实际只看到当前用户消息，无法多轮记忆。
    // Spring AI 2.0 中 MessageChatMemoryAdvisor 会：
    //   - 请求前：从 chatMemory.get(conversationId) 加载历史消息拼到 prompt
    //   - 响应后：把本轮 user 消息与 assistant 完整回复写入 ChatMemory
    // 故无需再手动 chatMemory.add(conversationId, userMessage/assistantMessage)。
    //
    // API 说明：MessageChatMemoryAdvisor.Builder 没有 conversationId() 方法，
    // conversationId 必须通过 AdvisorSpec.param(ChatMemory.CONVERSATION_ID, ...) 传入。
    // 这里用 Consumer 形式同时挂载 advisor 实例与设置 conversationId。
    String conversationId = sessionId.toString();

    // 4. 记录开始时间
    Instant start = Instant.now();
    String messageId = UUID.randomUUID().toString();
    StringBuilder fullContent = new StringBuilder();
    // 4.1 生成幂等键：用于 MQ Consumer 端去重，防止 retry 3 次后重复投递导致重复插入。
    // 同一 SSE 流的 done 和 error 互斥触发，但保险起见仍用唯一 UUID 关联。
    String mqMsgId = UUID.randomUUID().toString();

    // 5. 网络搜索增强（可选）：开关开启且服务可用时，先在 boundedElastic 调度器上异步
    //    搜索（避免阻塞 Netty 事件循环），将结果注入发给 LLM 的 prompt，并先下发
    //    search 事件（客户端展示引用来源）。搜索失败/无结果时优雅降级为普通对话，
    //    不阻断主流程。可与 RAG 叠加：RAG 负责私有知识召回，网络搜索补充实时信息。
    Mono<List<WebSearchResult>> searchMono =
        webSearch && webSearchService.isEnabled()
            ? webSearchService
                .searchReactive(content)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(
                    e -> {
                      log.warn("[streamMessage][webSearch failed({}), degrade]", e.getMessage());
                      return Mono.just(List.of());
                    })
            : Mono.defer(
                () -> {
                  // 客户端请求了搜索但服务端未启用：明确告警，便于排查配置问题
                  if (webSearch) {
                    log.warn(
                        "[streamMessage][webSearch requested but disabled, check ai.web-search"
                            + " config (WEB_SEARCH_ENABLED/TAVILY_API_KEY)]");
                  }
                  return Mono.just(List.of());
                });

    return searchMono.flatMapMany(
        searchResults -> {
          // RAG 知识库检索（kbId 非空且服务可用时）：在 boundedElastic 上同步检索，避免
          // 阻塞 Netty 事件循环。检索结果既注入 prompt（私有知识召回），又作为 sources
          // 事件下发前端展示引用来源标注。失败时优雅降级为普通对话。
          Mono<List<Document>> ragMono =
              kbId != null && knowledgeBaseService != null && vectorStore != null
                  ? Mono.fromCallable(() -> retrieveRagChunks(kbId, content))
                      .subscribeOn(Schedulers.boundedElastic())
                      .onErrorResume(
                          e -> {
                            log.warn(
                                "[streamMessage][RAG retrieve failed({}), degrade]",
                                e.getMessage());
                            return Mono.just(List.of());
                          })
                  : Mono.just(List.of());

          return ragMono.flatMapMany(
              ragDocs -> {
                // 用户消息已按原文持久化（步骤 2），此处仅增强发给 LLM 的 prompt：
                // RAG 私有知识优先，网络搜索补充实时信息
                String llmPrompt = buildCombinedContext(searchResults, ragDocs, content);
                Flux<ServerSentEvent<String>> searchEventFlux =
                    searchResults.isEmpty()
                        ? Flux.empty()
                        : Flux.just(
                            ServerSentEvent.<String>builder()
                                .id(messageId)
                                .event("search")
                                .data(toSearchEventData(searchResults))
                                .build());
                // RAG 引用来源事件：先于消息块下发，前端展示知识库引用来源标注
                Flux<ServerSentEvent<String>> sourcesEventFlux =
                    ragDocs.isEmpty()
                        ? Flux.empty()
                        : Flux.just(
                            ServerSentEvent.<String>builder()
                                .id(messageId)
                                .event("sources")
                                .data(toReferencesEventData(ragDocs))
                                .build());

                // 6. 调用流式 API（挂载 memory advisor，使模型获得多轮上下文）
                //    RAG 已由上面的手动检索完成（上下文注入 + 引用下发），不再重复注册
                //    QuestionAnswerAdvisor，避免同一问题二次检索造成重复引用。
                var promptBuilder = chatClient.prompt().user(llmPrompt);
                applyAdvisors(promptBuilder, conversationId);
                return Flux.concat(
                    sourcesEventFlux,
                    searchEventFlux,
                    promptBuilder.stream()
                        .content()
                        .map(
                            chunk -> {
                              fullContent.append(chunk);
                              // SSE 规范要求解析器剥离 data: 后的一个可选前导空格。
                              // Spring 的 ServerSentEvent 序列化器写入 "data:VALUE"（无空格），
                              // 当 chunk 本身以空格开头时（如 " World"），SSE 行变成 "data: World"，
                              // 客户端解析器会错误剥离 chunk 中的有效空格，导致英文单词粘连。
                              // 解决方案：在 data 前加一个空格，使解析器剥离的是我们添加的空格，
                              // 而非 chunk 内容中的空格。
                              return ServerSentEvent.<String>builder()
                                  .id(messageId)
                                  .event("message")
                                  .data(" " + chunk)
                                  .build();
                            })
                        .concatWith(
                            Mono.fromCallable(
                                () -> {
                                  long latencyMs =
                                      Duration.between(start, Instant.now()).toMillis();

                                  // 同步持久化 assistant 消息到 MySQL，确保客户端在切换会话后
                                  // 重新加载消息时能立即获取到完整的 AI 回答。
                                  // 同时携带 mqMsgId 幂等键，MQ Consumer 端会通过 UNIQUE 索引去重，
                                  // 避免重复入库。MQ 仍保留用于 ES 索引双写。
                                  String fullReply = fullContent.toString();
                                  ChatMessage assistantMessage = new ChatMessage();
                                  assistantMessage.setSessionId(sessionId);
                                  assistantMessage.setRole("assistant");
                                  assistantMessage.setContent(fullReply);
                                  assistantMessage.setModelId(modelId);
                                  assistantMessage.setLatencyMs((int) latencyMs);
                                  assistantMessage.setMqMsgId(mqMsgId);
                                  messageService.saveMessage(assistantMessage);

                                  // A2：累计本次回复 Token 用量（供限额检查）
                                  tokenLimitService.recordUsage(userId, fullReply.length() / 4);

                                  // 通过 MQ 发送 ES 索引任务（Consumer 端通过 mq_msg_id 去重，
                                  // 同步已写入的会触发 DuplicateKeyException 被 Consumer 安全捕获）
                                  ChatSession session = sessionMapper.selectById(sessionId);
                                  messageLogProducer.sendLog(
                                      sessionId,
                                      userId,
                                      "assistant",
                                      fullReply,
                                      modelId,
                                      0,
                                      (int) latencyMs,
                                      mqMsgId,
                                      session != null ? session.getTitle() : null);

                                  // 更新会话统计
                                  if (session != null) {
                                    session.setMessageCount(session.getMessageCount() + 2);
                                    session.setTotalTokens(
                                        session.getTotalTokens() + fullReply.length() / 4);
                                    sessionMapper.updateById(session);
                                    // 首轮消息（messageCount==2 即 user+assistant 一轮）触发自动命名
                                    // 异步执行，不阻塞 SSE done 事件返回
                                    if (session.getMessageCount() == 2
                                        && "新对话".equals(session.getTitle())
                                        && sessionService != null) {
                                      Mono.fromRunnable(
                                              () ->
                                                  sessionService.generateTitleAsync(
                                                      sessionId, content))
                                          .subscribeOn(Schedulers.boundedElastic())
                                          .subscribe();
                                    }
                                  }

                                  // 发送 done 事件
                                  return ServerSentEvent.<String>builder()
                                      .id(messageId)
                                      .event("done")
                                      .data(
                                          "{\"messageId\":\""
                                              + messageId
                                              + "\",\"latencyMs\":"
                                              + latencyMs
                                              + "}")
                                      .build();
                                }))
                        .onErrorResume(
                            e -> {
                              log.error(
                                  "[streamMessage][sessionId({}) error({})]",
                                  sessionId,
                                  e.getMessage(),
                                  e);
                              // 错误时把已累积的 partial content 通过 MQ 持久化（带同一 mqMsgId 幂等）
                              // 异常发生在 map 之后 concatWith 之前时，fullContent 已累积部分内容
                              String partial = fullContent.toString();
                              if (!partial.isEmpty()) {
                                try {
                                  // 同步持久化部分内容（带 mqMsgId 幂等键）
                                  ChatMessage partialMsg = new ChatMessage();
                                  partialMsg.setSessionId(sessionId);
                                  partialMsg.setRole("assistant");
                                  partialMsg.setContent(partial + "\n\n[流式中断]");
                                  partialMsg.setModelId(modelId);
                                  partialMsg.setLatencyMs(
                                      (int) Duration.between(start, Instant.now()).toMillis());
                                  partialMsg.setMqMsgId(mqMsgId);
                                  messageService.saveMessage(partialMsg);

                                  // MQ 用于 ES 索引（Consumer 端去重）
                                  // 错误分支无法获取 session，title 传 null
                                  messageLogProducer.sendLog(
                                      sessionId,
                                      userId,
                                      "assistant",
                                      partial + "\n\n[流式中断]",
                                      modelId,
                                      0,
                                      (int) Duration.between(start, Instant.now()).toMillis(),
                                      mqMsgId,
                                      null);
                                } catch (Exception persistEx) {
                                  log.warn(
                                      "[streamMessage][persist partial failed: {}]",
                                      persistEx.getMessage());
                                }
                              }
                              return Flux.just(
                                  ServerSentEvent.<String>builder()
                                      .event("error")
                                      .data("{\"error\":\"" + e.getMessage() + "\"}")
                                      .build());
                            }));
              });
        });
  }

  /**
   * 为 prompt 挂载 ChatMemory Advisor（多轮记忆）。
   *
   * <p>RAG 检索已由 {@link #retrieveRagChunks} 在流编排时手动完成（上下文注入 + 引用来源下发）， 不再注册
   * QuestionAnswerAdvisor，避免同一问题二次检索造成重复引用。
   *
   * @param promptBuilder ChatClient 的 prompt 构建器
   * @param conversationId ChatMemory 会话 ID（= sessionId）
   */
  private void applyAdvisors(
      org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec promptBuilder,
      String conversationId) {
    promptBuilder.advisors(
        a ->
            a.advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .param(ChatMemory.CONVERSATION_ID, conversationId));
  }

  /**
   * 手动 RAG 检索（与 QuestionAnswerAdvisor 同参数）：校验 KB 存在且 active，按相似度阈值 召回 topK 分片，并通过 filterExpression
   * 限定只在指定知识库范围内检索（防止跨库"串答"）。
   *
   * @param kbId 知识库 ID
   * @param query 用户问题
   * @return 检索到的文档列表（KB 不可用时返回空列表）
   */
  private List<Document> retrieveRagChunks(Long kbId, String query) {
    var kb = knowledgeBaseService.getKnowledgeBase(kbId);
    if (kb == null || !"active".equalsIgnoreCase(kb.getStatus())) {
      log.warn("[retrieveRagChunks][kbId({}) not found or disabled, skip RAG]", kbId);
      return List.of();
    }
    double threshold = kb.getSimilarityThreshold() != null ? kb.getSimilarityThreshold() : 0.5;
    var searchRequest =
        SearchRequest.builder()
            .query(query)
            .topK(5)
            .similarityThreshold(threshold)
            .filterExpression("knowledgeBaseId == '" + kbId + "'")
            .build();
    return vectorStore.similaritySearch(searchRequest);
  }

  /**
   * 合并 RAG 私有知识 + 网络搜索上下文，注入发给 LLM 的 prompt。
   *
   * @param webResults 网络搜索结果（可空）
   * @param ragDocs RAG 检索分片（可空）
   * @param content 用户问题
   */
  private String buildCombinedContext(
      List<WebSearchResult> webResults, List<Document> ragDocs, String content) {
    StringBuilder sb = new StringBuilder();
    if (ragDocs != null && !ragDocs.isEmpty()) {
      sb.append("[知识库参考文档]\n");
      for (int i = 0; i < ragDocs.size(); i++) {
        sb.append(i + 1).append(". ").append(ragDocs.get(i).getText()).append('\n');
      }
      sb.append('\n');
    }
    if (webResults != null && !webResults.isEmpty()) {
      sb.append(webSearchService.buildContext(webResults)).append('\n');
    }
    sb.append("[用户问题]\n").append(content);
    return sb.toString();
  }

  /**
   * 将 RAG 检索结果序列化为 SSE sources 事件的 JSON 数据（客户端展示引用来源标注）。
   *
   * <p>格式：{"references":[{"docId":"...","score":0.8,"content":"..."},...]}
   */
  private String toReferencesEventData(List<Document> docs) {
    StringBuilder sb = new StringBuilder("{\"references\":[");
    for (int i = 0; i < docs.size(); i++) {
      if (i > 0) sb.append(',');
      Document d = docs.get(i);
      Object docIdMeta = d.getMetadata().get("documentId");
      String docIdStr = docIdMeta != null ? String.valueOf(docIdMeta) : d.getId();
      double score = d.getScore() != null ? d.getScore() : 0.0;
      sb.append("{\"docId\":")
          .append(jsonQuote(docIdStr))
          .append(",\"score\":")
          .append(score)
          .append(",\"content\":")
          .append(jsonQuote(d.getText()))
          .append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  /** JSON 字符串转义（双引号/反斜杠/换行），保证 SSE 事件 data 为合法 JSON。 */
  private String jsonQuote(String s) {
    if (s == null) return "\"\"";
    String escaped =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    return '"' + escaped + '"';
  }

  /**
   * 将搜索来源序列化为 SSE search 事件的 JSON 数据（客户端展示引用列表）。
   *
   * <p>格式：{"sources":["url1","url2",...]}
   */
  private String toSearchEventData(java.util.List<WebSearchResult> results) {
    StringBuilder sb = new StringBuilder("{\"sources\":[");
    for (int i = 0; i < results.size(); i++) {
      if (i > 0) sb.append(',');
      // URL 转义双引号与反斜杠，保证 JSON 合法
      String url = results.get(i).getUrl().replace("\\", "\\\\").replace("\"", "\\\"");
      sb.append('"').append(url).append('"');
    }
    sb.append("]}");
    return sb.toString();
  }

  /** 解析结果：实际使用的模型 ID 与 ChatClient（A5 降级后 modelId 为降级模型）。 */
  private record ResolvedModel(String modelId, ChatClient chatClient) {}

  /**
   * 主模型 + 降级链解析（A5）：主模型不可用时按 sys_model_route_policy 配置的优先级逐个尝试降级模型， 返回第一个可用的模型；全部不可用返回 (modelId,
   * null)。
   */
  private ResolvedModel resolveModelWithFallback(String modelId) {
    ChatClient primary = chatClientFactory.getChatClient(modelId);
    if (primary != null) {
      return new ResolvedModel(modelId, primary);
    }
    for (String fallback : modelRouteService.resolveFallbackChain(modelId)) {
      ChatClient client = chatClientFactory.getChatClient(fallback);
      if (client != null) {
        log.warn("[streamMessage][model({}) 不可用，降级到 ({})]", modelId, fallback);
        return new ResolvedModel(fallback, client);
      }
    }
    return new ResolvedModel(modelId, null);
  }
}
