package com.ai.api.agent.memory;

import com.ai.api.agent.config.AgentProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短期记忆实现（add-agent-runtime，滑动窗口 + LLM 压缩）。
 *
 * <p>策略：
 *
 * <ol>
 *   <li>消息数超过 {@code agent.memory.window-size} 时，丢弃最早的 system 之外的消息
 *   <li>Token 超过 {@code agent.memory.token-threshold} 时，触发摘要压缩：将前 N-2 条消息摘要成一条 summary 消息（M4
 *       阶段简化为字符串拼接，不调用 LLM；V2 接入 LLM）
 * </ol>
 *
 * <p>存储位置：进程内 {@link ConcurrentHashMap}（M4 单机简化实现）。V2 替换为 Redis Hash。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component("shortTermMemory")
@RequiredArgsConstructor
public class ShortTermMemory implements MemoryStore {

  private final AgentProperties agentProperties;

  /** runtimeId → 消息列表（按时间顺序）。 */
  private final Map<Long, List<Message>> store = new ConcurrentHashMap<>();

  @Override
  public void append(Long runtimeId, String role, String content, Map<String, Object> metadata) {
    Message msg = new Message(role, content, metadata, estimateTokens(content));
    store.computeIfAbsent(runtimeId, k -> new ArrayList<>()).add(msg);
    applyWindowPolicy(runtimeId);
    applyTokenPolicy(runtimeId);
  }

  @Override
  public List<Map<String, Object>> retrieve(Long runtimeId) {
    List<Message> messages = store.getOrDefault(runtimeId, new ArrayList<>());
    List<Map<String, Object>> result = new ArrayList<>();
    for (Message m : messages) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("role", m.role);
      map.put("content", m.content);
      if (m.metadata != null) map.put("metadata", m.metadata);
      result.add(map);
    }
    return result;
  }

  @Override
  public int tokenUsage(Long runtimeId) {
    List<Message> messages = store.getOrDefault(runtimeId, new ArrayList<>());
    return messages.stream().mapToInt(m -> m.estimatedTokens).sum();
  }

  @Override
  public void clear(Long runtimeId) {
    store.remove(runtimeId);
  }

  /** 滑动窗口策略：超过 window-size 时，保留最新 N 条（system 消息不计数）。 */
  private void applyWindowPolicy(Long runtimeId) {
    int windowSize = agentProperties.getMemory().getWindowSize();
    List<Message> messages = store.get(runtimeId);
    if (messages == null) return;
    if (messages.size() <= windowSize) return;

    // 分离 system 与其他
    List<Message> systemMsgs = new ArrayList<>();
    List<Message> otherMsgs = new ArrayList<>();
    for (Message m : messages) {
      if ("system".equalsIgnoreCase(m.role)) systemMsgs.add(m);
      else otherMsgs.add(m);
    }

    // 只保留 windowSize 条非 system 消息
    if (otherMsgs.size() > windowSize) {
      otherMsgs =
          new ArrayList<>(otherMsgs.subList(otherMsgs.size() - windowSize, otherMsgs.size()));
    }

    List<Message> result = new ArrayList<>(systemMsgs);
    result.addAll(otherMsgs);
    store.put(runtimeId, result);
    log.debug(
        "[ShortTermMemory][applyWindowPolicy][runtimeId({}) 窗口后 {} 条]", runtimeId, result.size());
  }

  /** Token 压缩策略：超过阈值时，将前 N-2 条非 system 消息摘要为一条 summary。 */
  private void applyTokenPolicy(Long runtimeId) {
    int threshold = agentProperties.getMemory().getTokenThreshold();
    List<Message> messages = store.get(runtimeId);
    if (messages == null) return;
    int totalTokens = messages.stream().mapToInt(m -> m.estimatedTokens).sum();
    if (totalTokens <= threshold) return;

    List<Message> systemMsgs = new ArrayList<>();
    List<Message> otherMsgs = new ArrayList<>();
    for (Message m : messages) {
      if ("system".equalsIgnoreCase(m.role)) systemMsgs.add(m);
      else otherMsgs.add(m);
    }

    if (otherMsgs.size() <= 2) return;

    // 压缩前 N-2 条
    List<Message> toCompress = otherMsgs.subList(0, otherMsgs.size() - 2);
    StringBuilder summary = new StringBuilder("[summary] ");
    int compressedTokens = 0;
    for (Message m : toCompress) {
      summary.append(m.role).append(": ").append(truncate(m.content, 100)).append(" | ");
      compressedTokens += m.estimatedTokens;
    }
    Message summaryMsg = new Message("system", summary.toString(), Map.of("compressed", true), 100);

    List<Message> result = new ArrayList<>(systemMsgs);
    result.add(summaryMsg);
    result.addAll(otherMsgs.subList(otherMsgs.size() - 2, otherMsgs.size()));
    store.put(runtimeId, result);
    log.info(
        "[ShortTermMemory][applyTokenPolicy][runtimeId({}) 压缩前 {} tokens → 压缩后 {} tokens]",
        runtimeId,
        totalTokens,
        tokenUsage(runtimeId));
  }

  /** 简化版 token 估算：按 4 字符/token。 */
  private int estimateTokens(String content) {
    if (content == null) return 0;
    return content.length() / 4 + 1;
  }

  private String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }

  /** 消息内部表示。 */
  private static class Message {
    final String role;
    final String content;
    final Map<String, Object> metadata;
    final int estimatedTokens;

    Message(String role, String content, Map<String, Object> metadata, int estimatedTokens) {
      this.role = role;
      this.content = content;
      this.metadata = metadata;
      this.estimatedTokens = estimatedTokens;
    }
  }
}
