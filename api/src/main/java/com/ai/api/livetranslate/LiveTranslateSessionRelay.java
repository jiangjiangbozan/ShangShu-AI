package com.ai.api.livetranslate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * 单条实时同传会话的双向转发器。
 *
 * <p>职责：把客户端（Android/Web/小程序）发来的百炼 Realtime 事件原样转发到上游 {@code
 * wss://dashscope.aliyuncs.com/api-ws/v1/realtime}，并把上游事件原样回传客户端。
 *
 * <p>与上游的会话握手由本类代管：收到 {@code session.created} 后，按连接时的查询参数（lang/mode） 构造并发送 {@code
 * session.update}，客户端无需知道百炼协议细节，也不接触 API Key。
 *
 * <p>生命周期：客户端连接建立 → 上游异步连接 → 上游 {@code session.created} → 下发 {@code session.update} → 客户端推流（{@code
 * input_audio_buffer.append}）→ 客户端发 {@code session.finish} → 上游回 {@code session.finished} → 双侧关闭。
 *
 * @author add-live-translate
 */
@Slf4j
class LiveTranslateSessionRelay implements WebSocket.Listener {

  /** 客户端事件白名单：仅透传音频与会话控制事件，其余丢弃。 */
  private static final List<String> CLIENT_ALLOWED_TYPES =
      List.of(
          "input_audio_buffer.append",
          "input_audio_buffer.clear",
          "input_audio_buffer.commit",
          "session.finish");

  /** 上游连接建立前最多缓冲的客户端事件数，超出视为异常客户端，直接断开。 */
  private static final int MAX_PENDING_EVENTS = 512;

  /** 目标语言别名 → 百炼 short code（session.translation.language）。 */
  private static final Map<String, String> LANG_CODES =
      Map.ofEntries(
          Map.entry("zh", "zh"),
          Map.entry("zh-cn", "zh"),
          Map.entry("chinese", "zh"),
          Map.entry("中文", "zh"),
          Map.entry("en", "en"),
          Map.entry("english", "en"),
          Map.entry("英文", "en"),
          Map.entry("ja", "ja"),
          Map.entry("japanese", "ja"),
          Map.entry("日文", "ja"),
          Map.entry("ko", "ko"),
          Map.entry("korean", "ko"),
          Map.entry("韩文", "ko"),
          Map.entry("fr", "fr"),
          Map.entry("french", "fr"),
          Map.entry("de", "de"),
          Map.entry("german", "de"),
          Map.entry("es", "es"),
          Map.entry("spanish", "es"),
          Map.entry("ru", "ru"),
          Map.entry("russian", "ru"),
          Map.entry("ar", "ar"),
          Map.entry("arabic", "ar"),
          Map.entry("it", "it"),
          Map.entry("pt", "pt"),
          Map.entry("th", "th"),
          Map.entry("vi", "vi"),
          Map.entry("id", "id"));

  private final LiveTranslateProperties props;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 下行（上游→客户端）发送串行锁：上游监听线程与清扫线程都会写。 */
  private final Object clientSendLock = new Object();

  /** 上游发送单线程串行化：java.net.http.WebSocket 消息必须按序完整发送。 */
  private final ExecutorService upstreamSendExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "live-translate-upstream-send");
            t.setDaemon(true);
            return t;
          });

  /** 上游连接建立前缓冲的客户端事件。 */
  private final ConcurrentLinkedQueue<String> pendingClientEvents = new ConcurrentLinkedQueue<>();

  private final org.springframework.web.socket.WebSocketSession clientSession;
  private final String targetLang;
  private final boolean audioOutput;

  private final HttpClient httpClient;
  private final AtomicBoolean upstreamOpen = new AtomicBoolean(false);

  /** 上游已接受会话配置（session.updated），此后客户端事件才可直接透传。 */
  private final AtomicBoolean sessionReady = new AtomicBoolean(false);

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private volatile WebSocket upstream;
  private volatile long startNanos = System.nanoTime();
  private volatile long lastActivityNanos = System.nanoTime();

  /** 上游连接是否已终止（正常 finished 或异常断开）。 */
  private volatile boolean upstreamDone = false;

  LiveTranslateSessionRelay(
      LiveTranslateProperties props,
      org.springframework.web.socket.WebSocketSession clientSession,
      String targetLang,
      boolean audioOutput,
      HttpClient httpClient) {
    this.props = props;
    this.clientSession = clientSession;
    this.targetLang = normalizeLang(targetLang);
    this.audioOutput = audioOutput;
    this.httpClient = httpClient;
  }

  /** 启动上游连接。失败时向客户端下发 error 事件并断开。 */
  void start() {
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      sendErrorToClient("server_error", "MODEL_API_KEY_MISSING", "未配置百炼 API Key");
      closeClient();
      return;
    }
    URI uri = URI.create(props.getBaseUrl() + "?model=" + props.getModel());
    httpClient
        .newWebSocketBuilder()
        .header("Authorization", "Bearer " + props.getApiKey())
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .buildAsync(uri, this)
        .whenComplete(
            (ws, error) -> {
              if (error != null) {
                log.error(
                    "[LiveTranslateRelay][上游连接失败 client={} uri={}]",
                    clientSession.getId(),
                    uri,
                    error);
                sendErrorToClient("upstream_unavailable", "UPSTREAM_CONNECT_FAILED", "实时同传上游连接失败");
                closeClient();
              }
            });
  }

  /**
   * 处理客户端发来的事件：校验白名单与音频大小后转发上游；上游未就绪时先缓冲。
   *
   * @return false 表示会话已被关闭，调用方应停止处理
   */
  boolean onClientMessage(String payload) {
    if (closed.get()) {
      return false;
    }
    lastActivityNanos = System.nanoTime();
    String type;
    try {
      JsonNode node = objectMapper.readTree(payload);
      type = node.path("type").asText("");
    } catch (Exception e) {
      sendErrorToClient("invalid_request_error", "BAD_JSON", "事件不是合法 JSON");
      return true;
    }
    if (!CLIENT_ALLOWED_TYPES.contains(type)) {
      // 会话配置由服务端代管，协议事件白名单外的一律丢弃（防止客户端构造任意上游请求）
      log.debug("[LiveTranslateRelay][丢弃非白名单客户端事件 type={}]", type);
      return true;
    }
    if ("input_audio_buffer.append".equals(type)) {
      if (!validateAudioPayload(payload)) {
        return true;
      }
    }
    if (sessionReady.get()) {
      sendUpstream(payload);
    } else {
      // session.update 之前到达的音频会让上游判定「会话已开始」而拒绝后续配置，
      // 因此 session.updated 前一律缓冲，确认后再按序冲刷
      pendingClientEvents.add(payload);
      if (pendingClientEvents.size() > MAX_PENDING_EVENTS) {
        log.warn("[LiveTranslateRelay][上游未就绪期间缓冲溢出 client={} 关闭会话]", clientSession.getId());
        closeAll();
        return false;
      }
    }
    return true;
  }

  /** 会话活跃信息（清扫线程用）。 */
  long getStartNanos() {
    return startNanos;
  }

  long getLastActivityNanos() {
    return lastActivityNanos;
  }

  boolean isClosed() {
    return closed.get();
  }

  /** 关闭整条链路（客户端 + 上游 + 线程）。 */
  void closeAll() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    closeUpstream();
    closeClient();
    upstreamSendExecutor.shutdownNow();
  }

  // ==================== 上游事件处理 ====================

  @Override
  public void onOpen(WebSocket ws) {
    upstream = ws;
    upstreamOpen.set(true);
    log.debug("[LiveTranslateRelay][上游已连接 client={}]", clientSession.getId());
    ws.request(1);
  }

  /** 上游事件分片聚合缓冲（java.net.http.WebSocket 大消息可能分帧到达）。 */
  private final StringBuilder upstreamTextBuffer = new StringBuilder();

  @Override
  public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
    if (last) {
      if (upstreamTextBuffer.length() == 0) {
        handleUpstreamMessage(data.toString());
      } else {
        upstreamTextBuffer.append(data);
        handleUpstreamMessage(upstreamTextBuffer.toString());
        upstreamTextBuffer.setLength(0);
      }
    } else {
      upstreamTextBuffer.append(data);
    }
    ws.request(1);
    return null;
  }

  @Override
  public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
    log.debug(
        "[LiveTranslateRelay][上游关闭 client={} code={} reason={}]",
        clientSession.getId(),
        statusCode,
        reason);
    upstreamDone = true;
    upstreamOpen.set(false);
    closeAll();
    return null;
  }

  @Override
  public void onError(WebSocket ws, Throwable error) {
    log.error("[LiveTranslateRelay][上游异常 client={}]", clientSession.getId(), error);
    upstreamDone = true;
    upstreamOpen.set(false);
    sendErrorToClient("upstream_unavailable", "UPSTREAM_ERROR", "实时同传上游异常");
    closeAll();
  }

  /** 处理上游完整事件：session.created 触发代管 session.update，其余原样回传客户端。 */
  private void handleUpstreamMessage(String message) {
    if (closed.get()) {
      return;
    }
    lastActivityNanos = System.nanoTime();
    String type = "";
    try {
      JsonNode node = objectMapper.readTree(message);
      type = node.path("type").asText("");
    } catch (Exception ignore) {
      // 非 JSON 上游消息按原文透传
    }
    if ("session.created".equals(type)) {
      sendUpstream(buildSessionUpdate());
      log.info(
          "[LiveTranslateRelay][上游会话已建立 client={} lang={} audioOutput={}]",
          clientSession.getId(),
          targetLang,
          audioOutput);
    }
    if ("session.updated".equals(type)) {
      sessionReady.set(true);
      String pending;
      while ((pending = pendingClientEvents.poll()) != null) {
        sendUpstream(pending);
      }
    }
    sendToClient(message);
    if ("session.finished".equals(type)) {
      // 上游已确认收尾；服务端主动断开上游，客户端收到 finished 后自行关闭
      upstreamDone = true;
      closeUpstream();
    }
  }

  /** 构造服务端代管的 session.update：仅音频输入，输出模态按连接参数决定。 */
  private String buildSessionUpdate() {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("type", "session.update");
    ObjectNode session = root.putObject("session");
    session.putArray("input_modalities").add("audio");
    ArrayNode outputModalities = session.putArray("output_modalities");
    if (audioOutput) {
      outputModalities.add("text");
      outputModalities.add("audio");
    } else {
      outputModalities.add("text");
    }
    ObjectNode audio = session.putObject("audio");
    ObjectNode input = audio.putObject("input");
    ObjectNode format = input.putObject("format");
    format.put("type", "pcm");
    format.put("sample_rate", 16000);
    // turn_detection 缺省 = speaker_detection(0.5)：自动断句、静音不计费
    ObjectNode translation = session.putObject("translation");
    translation.put("language", targetLang);
    return root.toString();
  }

  // ==================== 发送与校验 ====================

  private boolean validateAudioPayload(String payload) {
    try {
      JsonNode node = objectMapper.readTree(payload);
      String audio = node.path("audio").asText("");
      if (audio.isEmpty()) {
        sendErrorToClient("invalid_request_error", "EMPTY_AUDIO", "audio 字段为空");
        return false;
      }
      byte[] decoded = Base64.getDecoder().decode(audio.getBytes(StandardCharsets.US_ASCII));
      if (decoded.length > props.getMaxAudioBytes()) {
        sendErrorToClient(
            "invalid_request_error",
            "AUDIO_TOO_LARGE",
            "单条音频超过上限 " + props.getMaxAudioBytes() + " 字节，请缩小分片（建议 100ms/片）");
        return false;
      }
      return true;
    } catch (IllegalArgumentException e) {
      sendErrorToClient("invalid_request_error", "BAD_BASE64", "audio 不是合法 Base64");
      return false;
    } catch (Exception e) {
      sendErrorToClient("invalid_request_error", "BAD_JSON", "事件解析失败");
      return false;
    }
  }

  private void sendUpstream(String message) {
    WebSocket ws = upstream;
    if (ws == null || closed.get()) {
      return;
    }
    upstreamSendExecutor.execute(
        () -> {
          try {
            ws.sendText(message, true);
          } catch (Exception e) {
            log.warn("[LiveTranslateRelay][上游发送失败 client={}]", clientSession.getId(), e);
          }
        });
  }

  private void sendToClient(String message) {
    if (closed.get()) {
      return;
    }
    try {
      synchronized (clientSendLock) {
        clientSession.sendMessage(new org.springframework.web.socket.TextMessage(message));
      }
    } catch (Exception e) {
      log.warn("[LiveTranslateRelay][客户端发送失败 client={} 关闭链路]", clientSession.getId(), e);
      closeAll();
    }
  }

  private void sendErrorToClient(String errorType, String code, String message) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("type", "error");
    ObjectNode error = root.putObject("error");
    error.put("type", errorType);
    error.put("code", code);
    error.put("message", message);
    sendToClient(root.toString());
  }

  private void closeClient() {
    try {
      clientSession.close(org.springframework.web.socket.CloseStatus.GOING_AWAY);
    } catch (Exception ignore) {
      // 客户端可能已断开
    }
  }

  private void closeUpstream() {
    WebSocket ws = upstream;
    if (ws == null) {
      return;
    }
    try {
      CompletableFuture<WebSocket> f = ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
      f.whenComplete(
          (w, e) -> {
            if (w != null) {
              w.abort();
            }
          });
    } catch (Exception ignore) {
      try {
        ws.abort();
      } catch (Exception ignore2) {
        // 已断开
      }
    }
  }

  private String normalizeLang(String lang) {
    if (lang == null || lang.isBlank()) {
      return normalizeLang(props.getTargetLang());
    }
    String key = lang.trim().toLowerCase();
    return LANG_CODES.getOrDefault(key, key);
  }

  /**
   * 判断会话是否需要被清扫线程回收。
   *
   * @return true 表示超时（会话超长或长期空闲）
   */
  boolean isExpired() {
    long now = System.nanoTime();
    long sessionSeconds = (now - startNanos) / 1_000_000_000L;
    long idleSeconds = (now - lastActivityNanos) / 1_000_000_000L;
    return sessionSeconds > props.getMaxSessionMinutes() * 60L
        || idleSeconds > props.getMaxIdleSeconds();
  }

  /** 上游是否已结束（含异常）。 */
  boolean isUpstreamDone() {
    return upstreamDone;
  }

  /** 仅供清扫线程使用的空闲队列占位校验（当前实现缓冲队列上限在入队时判断）。 */
  int pendingBufferSize() {
    return pendingClientEvents.size();
  }
}
