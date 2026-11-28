package com.ai.api.livetranslate;

import com.ai.api.identity.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 实时同传 WebSocket 端点：{@code /ws/live-translate}。
 *
 * <p>连接方式（客户端均不持有百炼 API Key）：
 *
 * <pre>
 * ws(s)://host:port/ws/live-translate?token=&lt;accessToken&gt;&amp;lang=en&amp;mode=text
 * </pre>
 *
 * <ul>
 *   <li>{@code token}（必填）：登录接口返回的 accessToken，握手时校验，非法直接 401 拒绝升级
 *   <li>{@code lang}（可选）：翻译目标语言 short code（zh/en/ja/ko/...），默认取配置 livetranslate.target-lang
 *   <li>{@code mode}（可选）：{@code text} 仅字幕（默认，省输出音频 token）；{@code audio} 字幕 + 译音（PCM 24kHz）
 * </ul>
 *
 * <p>连接建立后客户端直接推 {@code input_audio_buffer.append}（Base64 PCM 16kHz 16bit 单声道，建议 100ms/片），字幕增量从
 * {@code response.text.delta}（mode=text）或 {@code
 * response.audio_transcript.delta}（mode=audio）读取；原文识别增量在 {@code
 * conversation.item.input_audio_transcription.delta}（ASR 免费恒开）。结束发 {@code session.finish}， 收到
 * {@code session.finished} 后关闭连接。会话配置（session.update）由服务端代管，客户端无需发送。
 *
 * <p>资源防护：单连接会话时长上限 + 空闲超时清扫，防止上游连接泄漏导致按音频时长持续计费。
 *
 * @author add-live-translate
 */
@Slf4j
@Component
public class LiveTranslateHandler extends TextWebSocketHandler {

  private static final String ATTR_USER_ID = "liveTranslate.userId";
  private static final String ATTR_LANG = "liveTranslate.lang";
  private static final String ATTR_AUDIO_OUTPUT = "liveTranslate.audioOutput";

  /** 供 HandshakeInterceptor 写入同一组属性键。 */
  public static final String KEY_USER_ID = ATTR_USER_ID;

  public static final String KEY_LANG = ATTR_LANG;
  public static final String KEY_AUDIO_OUTPUT = ATTR_AUDIO_OUTPUT;

  private final LiveTranslateProperties props;
  private final TokenService tokenService;
  private final HttpClient httpClient;

  /** 在线会话注册表：sessionId → 转发器。 */
  private final Map<String, LiveTranslateSessionRelay> relays = new ConcurrentHashMap<>();

  private final ScheduledExecutorService sweeper =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "live-translate-sweeper");
            t.setDaemon(true);
            return t;
          });

  /** 当前活跃连接数（供监控日志）。 */
  private final AtomicInteger activeSessions = new AtomicInteger();

  public LiveTranslateHandler(LiveTranslateProperties props, TokenService tokenService) {
    this.props = props;
    this.tokenService = tokenService;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  @PostConstruct
  void startSweeper() {
    sweeper.scheduleWithFixedDelay(this::sweep, 30, 30, TimeUnit.SECONDS);
  }

  @PreDestroy
  void shutdown() {
    sweeper.shutdownNow();
    relays.values().forEach(LiveTranslateSessionRelay::closeAll);
    relays.clear();
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    if (!props.isEnabled()) {
      closeQuietly(session, CloseStatus.SERVER_ERROR);
      return;
    }
    Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
    if (userId == null) {
      closeQuietly(session, CloseStatus.NOT_ACCEPTABLE);
      return;
    }
    String lang = (String) session.getAttributes().get(ATTR_LANG);
    boolean audioOutput = Boolean.TRUE.equals(session.getAttributes().get(ATTR_AUDIO_OUTPUT));
    LiveTranslateSessionRelay relay =
        new LiveTranslateSessionRelay(props, session, lang, audioOutput, httpClient);
    relays.put(session.getId(), relay);
    log.info(
        "[LiveTranslateHandler][连接建立 session={} user={} lang={} mode={} active={}]",
        session.getId(),
        userId,
        lang,
        audioOutput ? "audio" : "text",
        activeSessions.incrementAndGet());
    relay.start();
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    LiveTranslateSessionRelay relay = relays.get(session.getId());
    if (relay == null) {
      return;
    }
    boolean alive = relay.onClientMessage(message.getPayload());
    if (!alive) {
      closeQuietly(session, CloseStatus.POLICY_VIOLATION);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    LiveTranslateSessionRelay relay = relays.remove(session.getId());
    if (relay != null) {
      relay.closeAll();
      log.info(
          "[LiveTranslateHandler][连接关闭 session={} status={} active={}]",
          session.getId(),
          status,
          activeSessions.decrementAndGet());
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.warn("[LiveTranslateHandler][传输异常 session={}]", session.getId(), exception);
    LiveTranslateSessionRelay relay = relays.remove(session.getId());
    if (relay != null) {
      relay.closeAll();
      activeSessions.decrementAndGet();
    }
  }

  /** 清扫超时会话：会话超长或空闲过久，防止上游按音频时长持续计费。 */
  private void sweep() {
    try {
      relays
          .values()
          .removeIf(
              relay -> {
                if (relay.isClosed()) {
                  return true;
                }
                if (relay.isExpired()
                    || (relay.isUpstreamDone()
                        && relay.getLastActivityNanos() < System.nanoTime() - 5_000_000_000L)) {
                  log.info("[LiveTranslateHandler][清扫超时会话 relay 关闭]");
                  relay.closeAll();
                  return true;
                }
                return false;
              });
    } catch (Exception e) {
      log.warn("[LiveTranslateHandler][清扫异常]", e);
    }
  }

  private void closeQuietly(WebSocketSession session, CloseStatus status) {
    try {
      session.close(status);
    } catch (Exception ignore) {
      // 已断开
    }
  }

  /** 供 HandshakeInterceptor 解析 token，握手阶段即完成鉴权。 */
  public Long parseTokenUserId(String token) {
    Claims claims = tokenService.parseAccessToken(token);
    return claims.get(TokenService.CLAIM_USER_ID, Long.class);
  }
}
