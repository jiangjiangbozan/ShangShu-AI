package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * HTTP 请求节点执行器。
 *
 * <p>支持 GET/POST/PUT/DELETE/PATCH，变量插值 URL/headers/body。 输出：statusCode、headers、body（自动尝试 JSON 解析为
 * Map）。
 */
@Slf4j
@Component
public class HttpRequestNodeExecutor implements NodeExecutor {

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  @Override
  public String type() {
    return "http-request";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) throws Exception {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String method = (String) data.getOrDefault("method", "GET");
    String url = resolveString((String) data.getOrDefault("url", ""), vars);
    Map<String, String> headers = (Map<String, String>) data.getOrDefault("headers", Map.of());
    String body = (String) data.getOrDefault("body", null);
    int timeout = ((Number) data.getOrDefault("timeout", 30000)).intValue();

    if (url.isBlank()) {
      throw new IllegalArgumentException("HTTP Request node: url is required");
    }

    // 变量插值 headers
    Map<String, String> resolvedHeaders = new HashMap<>();
    for (Map.Entry<String, String> e : headers.entrySet()) {
      resolvedHeaders.put(e.getKey(), resolveString(e.getValue(), vars));
    }

    // 构建 HttpRequest
    HttpRequest.Builder reqBuilder =
        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMillis(timeout));

    for (Map.Entry<String, String> e : resolvedHeaders.entrySet()) {
      reqBuilder.header(e.getKey(), e.getValue());
    }

    String resolvedBody = body != null ? resolveString(body, vars) : null;

    switch (method.toUpperCase()) {
      case "GET" -> reqBuilder.GET();
      case "POST" ->
          reqBuilder.POST(
              HttpRequest.BodyPublishers.ofString(resolvedBody != null ? resolvedBody : ""));
      case "PUT" ->
          reqBuilder.PUT(
              HttpRequest.BodyPublishers.ofString(resolvedBody != null ? resolvedBody : ""));
      case "DELETE" -> reqBuilder.DELETE();
      case "PATCH" ->
          reqBuilder.method(
              "PATCH",
              HttpRequest.BodyPublishers.ofString(resolvedBody != null ? resolvedBody : ""));
      default -> reqBuilder.GET();
    }

    // 如果有 body 且无 Content-Type，默认 application/json
    if (resolvedBody != null && !resolvedHeaders.containsKey("Content-Type")) {
      reqBuilder.header("Content-Type", "application/json");
    }

    HttpResponse<String> response =
        HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("statusCode", response.statusCode());
    outputs.put("headers", response.headers().map());
    outputs.put("body", response.body());

    log.info(
        "[HttpRequestNode][{}][{} {} -> {}]", context.nodeId(), method, url, response.statusCode());
    return NodeResult.of(outputs);
  }

  private String resolveString(String template, VariablePool vars) {
    if (template == null) return "";
    Object resolved = vars.resolve(template);
    return resolved != null ? String.valueOf(resolved) : template;
  }
}
