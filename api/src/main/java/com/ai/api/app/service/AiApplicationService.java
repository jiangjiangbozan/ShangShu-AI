package com.ai.api.app.service;

import static com.ai.api.common.exception.ErrorCode.COMMON_BAD_PARAM;
import static com.ai.api.common.exception.ErrorCode.COMMON_NOT_FOUND;

import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.runner.RunnerOutput;
import com.ai.api.agent.runner.StrategyFactory;
import com.ai.api.agent.runtime.AgentContext;
import com.ai.api.agent.runtime.AgentContextManager;
import com.ai.api.agent.runtime.AgentRuntimeService;
import com.ai.api.app.entity.AiApplication;
import com.ai.api.app.mapper.AiApplicationMapper;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.knowledge.service.RagRetrievalService;
import com.ai.api.model.config.ChatClientFactory;
import com.ai.api.workflow.entity.WfWorkflowRun;
import com.ai.api.workflow.service.WfWorkflowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 应用发布 Service（V20261121）。
 *
 * <p>负责 AI 应用的 CRUD、发布生成 API Key、以及对外统一调用入口（根据 type 路由到 Chat / RAG / Agent / Workflow 四种引擎）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiApplicationService {

  /** 公开入口策略：登录用户可用。 */
  public static final String ACCESS_POLICY_AUTHENTICATED = "AUTHENTICATED";

  /** 公开入口策略：匿名可用。 */
  public static final String ACCESS_POLICY_PUBLIC = "PUBLIC";

  /** 公开入口策略：指定角色可用。 */
  public static final String ACCESS_POLICY_ROLE_BASED = "ROLE_BASED";

  private static final String DEFAULT_ACCESS_POLICY = ACCESS_POLICY_AUTHENTICATED;
  private static final int PUBLIC_CODE_BYTES = 12;
  private static final int PUBLIC_CODE_RETRY_LIMIT = 8;

  private final AiApplicationMapper mapper;
  private final AgentContextManager agentContextManager;
  private final AgentRuntimeService agentRuntimeService;
  private final ChatClientFactory chatClientFactory;
  private final RagRetrievalService ragRetrievalService;
  private final StrategyFactory strategyFactory;
  private final WfWorkflowService wfWorkflowService;

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final SecureRandom RAND = new SecureRandom();

  // ============ CRUD ============

  public Page<AiApplication> list(int page, int size) {
    return mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
  }

  public AiApplication get(Long id) {
    AiApplication app = mapper.selectById(id);
    if (app == null) {
      throw new BusinessException(COMMON_NOT_FOUND, "AI 应用不存在");
    }
    return app;
  }

  @Transactional
  public AiApplication create(AiApplication app, String username) {
    app.setCreator(username);
    if (app.getEnabled() == null) app.setEnabled(true);
    if (app.getAccessPolicy() == null) app.setAccessPolicy(DEFAULT_ACCESS_POLICY);
    if (app.getPublicCode() == null || app.getPublicCode().isBlank()) {
      app.setPublicCode(generatePublicCode());
    }
    mapper.insert(app);
    return app;
  }

  @Transactional
  public AiApplication update(Long id, AiApplication update) {
    AiApplication existing = mapper.selectById(id);
    if (existing == null) {
      throw new BusinessException(COMMON_NOT_FOUND, "AI 应用不存在");
    }
    existing.setAccessPolicy(update.getAccessPolicy());
    existing.setConfigJson(update.getConfigJson());
    existing.setDescription(update.getDescription());
    existing.setEnabled(update.getEnabled());
    existing.setName(update.getName());
    existing.setPublicCode(update.getPublicCode());
    existing.setRequiredRole(update.getRequiredRole());
    existing.setType(update.getType());
    mapper.updateById(existing);
    return existing;
  }

  @Transactional
  public void delete(Long id) {
    mapper.deleteById(id);
  }

  // ============ 发布 / 鉴权 ============

  /**
   * 发布 AI 应用：生成随机 API Key，SHA256 存库（明文仅返回一次）。
   *
   * @return 明文 apiKey（仅本次返回）
   */
  @Transactional
  public String publish(Long id) {
    AiApplication app = mapper.selectById(id);
    if (app == null) {
      throw new BusinessException(COMMON_NOT_FOUND, "AI 应用不存在");
    }
    String rawKey =
        "sk-app-" + Base64.getUrlEncoder().withoutPadding().encodeToString(generateRandomBytes(32));
    String hash = sha256Hex(rawKey);
    app.setApiKeyHash(hash);
    app.setEnabled(true);
    if (app.getPublicCode() == null || app.getPublicCode().isBlank()) {
      app.setPublicCode(generatePublicCode());
    }
    if (app.getAccessPolicy() == null) app.setAccessPolicy(DEFAULT_ACCESS_POLICY);
    mapper.updateById(app);
    log.info("[AiApplicationService][publish] appId={}, hashPrefix={}", id, hash.substring(0, 8));
    return rawKey;
  }

  /** 通过明文 apiKey 查库（内部 hash 后匹配 api_key_hash）。 */
  public AiApplication findByApiKey(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return null;
    }
    String hash = sha256Hex(apiKey);
    AiApplication app =
        mapper.selectOne(
            new LambdaQueryWrapper<AiApplication>().eq(AiApplication::getApiKeyHash, hash));
    if (app != null && !Boolean.TRUE.equals(app.getEnabled())) {
      return null;
    }
    return app;
  }

  /**
   * 按公开标识查询已发布应用；公开入口只认 publicCode，不接触 apiKey。
   *
   * @param publicCode 二维码/小程序 scene 携带的非敏感标识
   * @return 已发布且启用的应用，否则 null
   */
  public AiApplication findByPublicCode(String publicCode) {
    if (publicCode == null || publicCode.isBlank()) {
      return null;
    }
    AiApplication app =
        mapper.selectOne(
            new LambdaQueryWrapper<AiApplication>()
                .eq(AiApplication::getPublicCode, publicCode)
                .eq(AiApplication::getEnabled, true)
                .isNotNull(AiApplication::getApiKeyHash));
    if (app != null && app.getAccessPolicy() == null) {
      app.setAccessPolicy(DEFAULT_ACCESS_POLICY);
    }
    return app;
  }

  /**
   * 查询当前身份可见的已发布应用：通用策略直接放行，角色策略按数据库字段下推过滤。
   *
   * @param roles 当前用户角色编码，匿名访问时为空集合
   * @return 可见应用列表
   */
  public List<AiApplication> listPublished(Set<String> roles) {
    return mapper.selectList(
        new LambdaQueryWrapper<AiApplication>()
            .eq(AiApplication::getEnabled, true)
            .isNotNull(AiApplication::getApiKeyHash)
            .and(
                wrapper -> {
                  wrapper.in(
                      AiApplication::getAccessPolicy,
                      List.of(ACCESS_POLICY_PUBLIC, ACCESS_POLICY_AUTHENTICATED));
                  if (roles != null && !roles.isEmpty()) {
                    wrapper.or().in(AiApplication::getRequiredRole, roles);
                  }
                }));
  }

  /** 生成全局唯一的公开标识；冲突时按固定次数重试，避免把唯一约束异常暴露给调用方。 */
  private String generatePublicCode() {
    for (int attempt = 0; attempt < PUBLIC_CODE_RETRY_LIMIT; attempt++) {
      String candidate =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(generateRandomBytes(PUBLIC_CODE_BYTES));
      Long count =
          mapper.selectCount(
              new LambdaQueryWrapper<AiApplication>().eq(AiApplication::getPublicCode, candidate));
      if (count != null && count == 0L) {
        return candidate;
      }
    }
    throw new BusinessException(COMMON_BAD_PARAM, "无法生成唯一公开标识，请稍后重试");
  }

  // ============ 对外统一调用入口 ============

  /**
   * AI 应用 chat 调用（OpenAI 兼容入口）。
   *
   * <p>根据 {@code app.type} 路由到不同引擎：
   *
   * <ul>
   *   <li>{@code chat}：纯 ChatClient 调用（systemPrompt + modelId 从 config_json 取）
   *   <li>{@code rag}：先 RAG 检索（kbId 从 config_json 取），再 ChatClient
   *   <li>{@code agent}：创建 Agent runtime 同步执行（agentConfigId 从 config_json 取，直连 Runner 不经 MQ）
   *   <li>{@code workflow}：触发 WorkflowEngine 同步执行（workflowId 从 config_json 取）
   * </ul>
   *
   * @param app 已发布且启用的 AI 应用
   * @param userMessage 用户消息
   * @param history 多轮历史 [{role, content}]，可为 null
   * @return 模型回复内容
   */
  public String chat(AiApplication app, String userMessage, List<Map<String, String>> history) {
    if (userMessage == null || userMessage.isBlank()) {
      throw new BusinessException(COMMON_BAD_PARAM, "用户消息不能为空");
    }
    JsonNode cfg = parseConfig(app.getConfigJson());
    return switch (app.getType() == null ? "chat" : app.getType()) {
      case "chat" -> runChat(cfg, userMessage, history);
      case "rag" -> runRag(cfg, userMessage, history);
      case "agent" -> runAgent(cfg, userMessage);
      case "workflow" -> runWorkflow(cfg, userMessage);
      default -> throw new BusinessException(COMMON_BAD_PARAM, "未知应用类型: " + app.getType());
    };
  }

  private String runChat(JsonNode cfg, String userMessage, List<Map<String, String>> history) {
    String modelId = text(cfg, "modelId", "default");
    String systemPrompt = text(cfg, "systemPrompt", "你是一个友好的 AI 助手。");
    ChatClient client = chatClientFactory.getChatClient(modelId);
    if (client == null) {
      throw new BusinessException(COMMON_BAD_PARAM, "模型不可用: " + modelId);
    }
    String prompt =
        "System: " + systemPrompt + "\n\n" + transcript(history) + "User: " + userMessage;
    return client.prompt().user(prompt).call().content();
  }

  private String runRag(JsonNode cfg, String userMessage, List<Map<String, String>> history) {
    long kbId = cfg.path("kbId").asLong(0);
    if (kbId == 0) {
      throw new BusinessException(COMMON_BAD_PARAM, "RAG 应用缺少 kbId 配置");
    }
    int topK = cfg.path("topK").asInt(5);
    String modelId = text(cfg, "modelId", "default");
    String systemPrompt = text(cfg, "systemPrompt", "你是一个知识库问答助手，请基于提供的参考文档回答用户问题。");

    // 1. RAG 检索
    var docs = ragRetrievalService.retrieveWithRrf(kbId, userMessage, topK);
    StringBuilder context = new StringBuilder("[参考文档]\n");
    for (int i = 0; i < docs.size(); i++) {
      context.append(i + 1).append(". ").append(docs.get(i).getText()).append('\n');
    }

    // 2. ChatClient 调用
    ChatClient client = chatClientFactory.getChatClient(modelId);
    if (client == null) {
      throw new BusinessException(COMMON_BAD_PARAM, "模型不可用: " + modelId);
    }
    String prompt =
        systemPrompt + "\n\n" + context + "\n" + transcript(history) + "[用户问题]\n" + userMessage;
    return client.prompt().user(prompt).call().content();
  }

  /** Agent 类型：创建 runtime → 用户问题写入上下文 → 直连 Runner 同步执行（不经 MQ，保证兼容端点同步返回）。 */
  private String runAgent(JsonNode cfg, String userMessage) {
    long agentConfigId = cfg.path("agentConfigId").asLong(0);
    if (agentConfigId == 0) {
      throw new BusinessException(COMMON_BAD_PARAM, "Agent 应用缺少 agentConfigId 配置");
    }
    Long runtimeId = agentRuntimeService.createRuntime(null, agentConfigId, null, null, null);
    AgentContext context = new AgentContext();
    context.setUserQuery(userMessage);
    agentContextManager.snapshot(runtimeId, context);
    AgentRuntime runtime = agentRuntimeService.loadRuntime(runtimeId);
    // 同步链路不经 MQ Consumer，需先转 running，否则 Runner 的 done() 会因 PENDING → DONE 被状态机拦截
    agentRuntimeService.markRunning(runtimeId);
    RunnerOutput output = strategyFactory.getRunner(runtime.getStrategy()).run(runtimeId);

    String answer = agentRuntimeService.loadRuntime(runtimeId).getFinalAnswer();
    if (answer == null || answer.isBlank()) {
      answer = output.getFinalAnswer();
    }
    if (answer == null || answer.isBlank()) {
      throw new BusinessException(COMMON_BAD_PARAM, "Agent 执行未产生最终回复");
    }
    return answer;
  }

  /**
   * Workflow 类型：以 config_json.workflowId 触发工作流（无 SSE），输入合并 config_json.inputs 预设与 query（用户消息），取 End
   * 节点约定的 output/answer/text 键作为回复。
   */
  private String runWorkflow(JsonNode cfg, String userMessage) {
    String workflowId = text(cfg, "workflowId", null);
    if (workflowId == null || workflowId.isBlank()) {
      throw new BusinessException(COMMON_BAD_PARAM, "Workflow 应用缺少 workflowId 配置");
    }
    Map<String, Object> inputs = new LinkedHashMap<>();
    // 预设 inputs（config_json.inputs 对象逐字段合入）
    JsonNode preset = cfg.path("inputs");
    if (preset.isObject()) {
      for (Iterator<String> it = preset.fieldNames(); it.hasNext(); ) {
        String name = it.next();
        JsonNode value = preset.get(name);
        inputs.put(name, value.isValueNode() ? value.asText() : value.toString());
      }
    }
    // 约定输入变量名 query：工作流内以 ${global.query} 引用用户消息
    inputs.put("query", userMessage);
    WfWorkflowRun run = wfWorkflowService.run(workflowId, inputs, null);
    Map<String, Object> outputs = parseOutputs(run.getOutputs());
    Object reply = outputs.get("output");
    if (reply == null) {
      reply = outputs.get("answer");
    }
    if (reply == null) {
      reply = outputs.get("text");
    }
    return stringifyReply(reply, outputs);
  }

  /**
   * 回复内容字符串化：String 原样返回；嵌套 Map/集合等复杂输出序列化为 JSON， 避免 Map.toString 产生 {@code {k=v}} 非标准格式导致调用方无法解析。
   */
  private String stringifyReply(Object reply, Map<String, Object> outputs) {
    if (reply instanceof String text) {
      return text;
    }
    try {
      return JSON.writeValueAsString(reply != null ? reply : outputs);
    } catch (JsonProcessingException e) {
      log.warn("[AiApplicationService][stringifyReply] 序列化失败: {}", e.getMessage());
      return String.valueOf(reply != null ? reply : outputs);
    }
  }

  /** 将多轮历史拼接为会话文本（无状态 ChatClient 的上下文注入方式）。 */
  private String transcript(List<Map<String, String>> history) {
    if (history == null || history.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("[对话历史]\n");
    for (Map<String, String> item : history) {
      String content = item.getOrDefault("content", "");
      if (content.isBlank()) {
        continue;
      }
      sb.append(
              "assistant".equalsIgnoreCase(item.getOrDefault("role", "user"))
                  ? "Assistant: "
                  : "User: ")
          .append(content)
          .append('\n');
    }
    return sb.isEmpty() ? "" : sb.append('\n').toString();
  }

  /** 解析运行记录的 outputs JSON（空/非法时返回空 Map）。 */
  private Map<String, Object> parseOutputs(String outputsJson) {
    if (outputsJson == null || outputsJson.isBlank()) {
      return Map.of();
    }
    try {
      return JSON.readValue(outputsJson, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      log.warn("[AiApplicationService][parseOutputs] 解析失败: {}", e.getMessage());
      return Map.of();
    }
  }

  // ============ 工具方法 ============

  private JsonNode parseConfig(String configJson) {
    if (configJson == null || configJson.isBlank()) {
      return JSON.createObjectNode();
    }
    try {
      return JSON.readTree(configJson);
    } catch (JsonProcessingException e) {
      log.warn("[AiApplicationService][parseConfig] JSON 解析失败: {}", e.getMessage());
      return JSON.createObjectNode();
    }
  }

  private String text(JsonNode node, String field, String def) {
    JsonNode n = node.path(field);
    return n.isMissingNode() || n.isNull() ? def : n.asText(def);
  }

  private static byte[] generateRandomBytes(int len) {
    byte[] bytes = new byte[len];
    RAND.nextBytes(bytes);
    return bytes;
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 不可用", e);
    }
  }
}
