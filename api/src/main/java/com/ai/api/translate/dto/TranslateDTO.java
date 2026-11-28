package com.ai.api.translate.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 翻译接口的请求与响应模型。
 *
 * <p>按后端约定，同一 Controller 的 DTO 集中在一个文件内，响应显式列出字段，避免暴露实体内部结构。
 *
 * @author cross-language
 */
public final class TranslateDTO {

  private TranslateDTO() {}

  /** 消息翻译请求：targetLang 为空时自动推断（中文→English，其余→Chinese）。 */
  @Data
  public static class MessageTranslateRequest {

    private String targetLang;
  }

  /** 消息翻译响应。 */
  @Builder
  @Data
  public static class MessageTranslateResponse {

    /** 消息 ID */
    private Long messageId;

    /** 源语言（推断值） */
    private String sourceLang;

    /** 目标语言（归一化后） */
    private String targetLang;

    /** 译文 */
    private String translated;
  }
}
