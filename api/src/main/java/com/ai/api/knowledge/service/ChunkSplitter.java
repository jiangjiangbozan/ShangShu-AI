package com.ai.api.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 文本分片器
 *
 * <p>采用"滑动窗口 + 段落感知"的简单分片策略： 1. 优先按双换行（段落）切分； 2. 不足目标长度则累积下一段落； 3. 超过 chunkSize 时按字符截断，并通过 overlap
 * 保证上下文连续性。
 *
 * <p>这种实现不依赖额外依赖，对 Markdown / 纯文本友好。若后续需要 TokenCountBased 的精确分片，可改用 Spring AI 内置
 * TokenTextSplitter（需引入 tokenizer 依赖）。
 *
 * @author add-rag-module
 */
@Component
public class ChunkSplitter {

  /**
   * 分片。
   *
   * @param text 原始文本
   * @param chunkSize 目标字符数（默认 800）
   * @param overlap 重叠字符数（默认 200）
   * @return 分片列表
   */
  public List<String> split(String text, int chunkSize, int overlap) {
    if (text == null || text.isBlank()) return List.of();
    if (chunkSize <= 0) chunkSize = 800;
    if (overlap < 0 || overlap >= chunkSize) overlap = 200;

    List<String> chunks = new ArrayList<>();
    // 按段落切
    String[] paragraphs = text.split("\\n{2,}");
    StringBuilder buffer = new StringBuilder(chunkSize);

    for (String paragraph : paragraphs) {
      String trimmed = paragraph.strip();
      if (trimmed.isEmpty()) continue;

      // 当前段落单段已超过 chunkSize，单独切
      if (trimmed.length() > chunkSize) {
        // 先把 buffer 推出
        if (buffer.length() > 0) {
          chunks.add(buffer.toString());
          buffer.setLength(0);
        }
        for (int i = 0; i < trimmed.length(); i += chunkSize - overlap) {
          int end = Math.min(i + chunkSize, trimmed.length());
          chunks.add(trimmed.substring(i, end));
          if (end == trimmed.length()) break;
        }
        continue;
      }

      if (buffer.length() + trimmed.length() + 1 > chunkSize) {
        // 满了：提交当前 buffer
        chunks.add(buffer.toString());
        // 保留 overlap 字符作为下一段起点
        if (overlap > 0 && buffer.length() > overlap) {
          String tail = buffer.substring(buffer.length() - overlap);
          buffer.setLength(0);
          buffer.append(tail);
        } else {
          buffer.setLength(0);
        }
      }
      if (buffer.length() > 0) buffer.append('\n');
      buffer.append(trimmed);
    }
    if (buffer.length() > 0) chunks.add(buffer.toString());
    return chunks;
  }
}
