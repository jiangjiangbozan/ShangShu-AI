package com.ai.api.knowledge.service;

import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

/**
 * 文档解析器
 *
 * <p>基于 Apache Tika 实现，支持 PDF / Word / Markdown / HTML / 纯文本等主流文档格式的文本提取。
 *
 * <p>设计要点： 1. AutoDetectParser 自动按文件头识别格式； 2. BodyContentHandler(-1) 关闭内容长度上限，避免大文档被截断； 3.
 * 重复空白压缩，便于后续分片。
 *
 * @author add-rag-module
 */
@Slf4j
@Component
public class DocumentParser {

  private static final int NO_WRITE_LIMIT = -1;

  /**
   * 解析输入流为纯文本。
   *
   * @param input 文件输入流
   * @return 纯文本
   */
  public String parse(InputStream input) {
    try {
      ContentHandler handler = new BodyContentHandler(NO_WRITE_LIMIT);
      Metadata metadata = new Metadata();
      ParseContext context = new ParseContext();
      AutoDetectParser parser = new AutoDetectParser();
      parser.parse(input, handler, metadata, context);

      String text = handler.toString();
      // 压缩连续空白行
      return text == null ? "" : text.replaceAll("\\n{3,}", "\n\n").trim();
    } catch (Exception e) {
      log.error("[DocumentParser][parse failed({})]", e.getMessage(), e);
      throw new IllegalStateException("文档解析失败: " + e.getMessage(), e);
    }
  }
}
