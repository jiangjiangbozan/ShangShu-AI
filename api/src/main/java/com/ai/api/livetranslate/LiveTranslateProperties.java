package com.ai.api.livetranslate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 实时同传配置（qwen3.8-livetranslate-flash-realtime）。
 *
 * <p>密钥链：{@code LIVETRANSLATE_API_KEY → DASHSCOPE_API_KEY → dashscope.api-key}（与 translation
 * 段一致，真实密钥存 application-secret.yml，不入仓库）。上游接入协议见阿里云百炼「Qwen-LiveTranslate-Realtime WebSocket 接入」：API
 * Key 只保存在服务端，浏览器/移动端仅持有本服务的短期 JWT。
 *
 * @author add-live-translate
 */
@Component
public class LiveTranslateProperties {

  private final boolean enabled;
  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final String targetLang;
  private final int maxAudioBytes;
  private final int maxSessionMinutes;
  private final int maxIdleSeconds;

  public LiveTranslateProperties(
      @Value("${livetranslate.enabled:true}") boolean enabled,
      @Value("${livetranslate.base-url:wss://dashscope.aliyuncs.com/api-ws/v1/realtime}")
          String baseUrl,
      @Value("${livetranslate.api-key:}") String apiKey,
      @Value("${livetranslate.model:qwen3.8-livetranslate-flash-realtime}") String model,
      @Value("${livetranslate.target-lang:en}") String targetLang,
      @Value("${livetranslate.max-audio-bytes:131072}") int maxAudioBytes,
      @Value("${livetranslate.max-session-minutes:30}") int maxSessionMinutes,
      @Value("${livetranslate.max-idle-seconds:120}") int maxIdleSeconds) {
    this.enabled = enabled;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.targetLang = targetLang;
    this.maxAudioBytes = maxAudioBytes;
    this.maxSessionMinutes = maxSessionMinutes;
    this.maxIdleSeconds = maxIdleSeconds;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** 上游 WebSocket 地址（不含 model 查询参数，连接时拼接）。 */
  public String getBaseUrl() {
    return baseUrl;
  }

  /** 百炼 API Key（仅服务端持有）。 */
  public String getApiKey() {
    return apiKey;
  }

  public String getModel() {
    return model;
  }

  /** 默认翻译目标语言（客户端可用查询参数 lang 覆盖）。 */
  public String getTargetLang() {
    return targetLang;
  }

  /** 单条 input_audio_buffer.append 允许的最大解码后音频字节数。 */
  public int getMaxAudioBytes() {
    return maxAudioBytes;
  }

  /** 单次同传会话时长上限（分钟），防止上游连接泄漏导致持续计费。 */
  public int getMaxSessionMinutes() {
    return maxSessionMinutes;
  }

  /** 无任何消息的最长空闲秒数，超时由清扫线程关闭会话。 */
  public int getMaxIdleSeconds() {
    return maxIdleSeconds;
  }
}
