package com.ai.api.livetranslate;

import io.jsonwebtoken.JwtException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 实时同传 WebSocket 装配。
 *
 * <p>端点 {@code /ws/live-translate}：握手阶段用查询参数 {@code token} 鉴权（浏览器 JS WebSocket 与微信小程序
 * wx.connectSocket 均无法自定义握手请求头，故 token 走查询参数；校验失败 401 拒绝升级）。 {@code /ws/**} 已在 SecurityConfig
 * 放行匿名授权，鉴权完全由本拦截器承担。
 *
 * <p>音频分片为 Base64 文本帧，Tomcat 默认 8KB 文本缓冲不够，放宽到 512KB。
 *
 * @author add-live-translate
 */
@Configuration
@EnableWebSocket
public class LiveTranslateWebSocketConfig implements WebSocketConfigurer {

  private final LiveTranslateHandler liveTranslateHandler;

  public LiveTranslateWebSocketConfig(LiveTranslateHandler liveTranslateHandler) {
    this.liveTranslateHandler = liveTranslateHandler;
  }

  @Bean
  public ServletServerContainerFactoryBean createWebSocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(512 * 1024);
    container.setMaxBinaryMessageBufferSize(512 * 1024);
    // 客户端可能暂停说话，空闲由业务侧清扫线程管理，容器超时放宽
    container.setMaxSessionIdleTimeout(10 * 60 * 1000L);
    return container;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(liveTranslateHandler, "/ws/live-translate")
        .addInterceptors(authInterceptor())
        .setAllowedOriginPatterns("*");
  }

  /** 握手拦截器：校验 accessToken 与 lang/mode 参数，写入会话属性。 */
  private HandshakeInterceptor authInterceptor() {
    return new HandshakeInterceptor() {
      @Override
      public boolean beforeHandshake(
          @NonNull ServerHttpRequest request,
          @NonNull ServerHttpResponse response,
          @NonNull WebSocketHandler wsHandler,
          @NonNull Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
          response.setStatusCode(HttpStatus.UNAUTHORIZED);
          return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
          response.setStatusCode(HttpStatus.UNAUTHORIZED);
          return false;
        }
        try {
          Long userId = liveTranslateHandler.parseTokenUserId(token);
          attributes.put(LiveTranslateHandler.KEY_USER_ID, userId);
        } catch (JwtException | IllegalArgumentException e) {
          response.setStatusCode(HttpStatus.UNAUTHORIZED);
          return false;
        }
        String lang = servletRequest.getServletRequest().getParameter("lang");
        attributes.put(
            LiveTranslateHandler.KEY_LANG, lang == null || lang.isBlank() ? null : lang.trim());
        String mode = servletRequest.getServletRequest().getParameter("mode");
        attributes.put(
            LiveTranslateHandler.KEY_AUDIO_OUTPUT, mode != null && "audio".equalsIgnoreCase(mode));
        return true;
      }

      @Override
      public void afterHandshake(
          @NonNull ServerHttpRequest request,
          @NonNull ServerHttpResponse response,
          @NonNull WebSocketHandler wsHandler,
          Exception exception) {
        // 无需处理
      }
    };
  }
}
