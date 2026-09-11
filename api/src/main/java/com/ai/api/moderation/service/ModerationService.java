package com.ai.api.moderation.service;

import com.ai.api.moderation.entity.ModerationConfig;
import com.ai.api.moderation.mapper.ModerationConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 内容审查服务（A4）：openai moderation API / 本地敏感词规则 两种审查方式。 */
@Slf4j
@Service
public class ModerationService {

  private final ModerationConfigMapper configMapper;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public ModerationService(ModerationConfigMapper configMapper) {
    this.configMapper = configMapper;
  }

  /** 审查结果。 */
  public record ModerationResult(boolean flagged, String reason) {}

  /** 对文本执行内容审查；未配置/停用时直接放行。 */
  public ModerationResult checkContent(String text) {
    if (text == null || text.isBlank()) {
      return new ModerationResult(false, null);
    }
    ModerationConfig config = activeConfig();
    if (config == null
        || Boolean.FALSE.equals(config.getEnabled())
        || "disabled".equals(config.getProvider())) {
      return new ModerationResult(false, null);
    }
    try {
      return switch (config.getProvider()) {
        case "openai" -> checkOpenAi(config, text);
        default -> checkKeyword(config, text);
      };
    } catch (Exception e) {
      // 审查服务异常时不阻断业务（fail-open），仅记录告警
      log.warn("[Moderation][check failed({}), fail-open]", e.getMessage());
      return new ModerationResult(false, null);
    }
  }

  /** 按指定配置执行一次审查（用于配置页测试按钮）。 */
  public ModerationResult test(
      String provider, String apiKey, String apiUrl, String threshold, String keywords, String text)
      throws IOException, InterruptedException {
    if (text == null || text.isBlank()) {
      return new ModerationResult(false, null);
    }
    if ("disabled".equals(provider) || provider == null || provider.isBlank()) {
      return new ModerationResult(false, null);
    }
    return switch (provider) {
      case "openai" -> {
        ModerationConfig c = new ModerationConfig();
        c.setApiKey(apiKey);
        c.setApiUrl(apiUrl);
        double t = 0.5;
        try {
          t = Double.parseDouble(threshold);
        } catch (Exception ignored) {
          // keep default
        }
        c.setThreshold(java.math.BigDecimal.valueOf(t));
        yield checkOpenAi(c, text);
      }
      default -> {
        ModerationConfig c = new ModerationConfig();
        c.setKeywords(keywords);
        yield checkKeyword(c, text);
      }
    };
  }

  private ModerationConfig activeConfig() {
    return configMapper.selectOne(
        new LambdaQueryWrapper<ModerationConfig>()
            .eq(ModerationConfig::getEnabled, true)
            .orderByAsc(ModerationConfig::getId)
            .last("LIMIT 1"));
  }

  private ModerationResult checkKeyword(ModerationConfig config, String text) {
    if (config.getKeywords() == null || config.getKeywords().isBlank()) {
      return new ModerationResult(false, null);
    }
    String lower = text.toLowerCase();
    List<String> words =
        Arrays.stream(config.getKeywords().split(","))
            .map(String::trim)
            .filter(w -> !w.isEmpty())
            .toList();
    for (String word : words) {
      if (lower.contains(word.toLowerCase())) {
        return new ModerationResult(true, "命中敏感词: " + word);
      }
    }
    return new ModerationResult(false, null);
  }

  private ModerationResult checkOpenAi(ModerationConfig config, String text)
      throws IOException, InterruptedException {
    String url =
        config.getApiUrl() != null && !config.getApiUrl().isBlank()
            ? config.getApiUrl()
            : "https://api.openai.com/v1/moderations";
    String body = objectMapper.writeValueAsString(Map.of("input", text));
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
      builder.header("Authorization", "Bearer " + config.getApiKey());
    }
    HttpResponse<String> response =
        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      log.warn("[Moderation][openai http {}: {}]", response.statusCode(), response.body());
      return new ModerationResult(false, null);
    }
    JsonNode root = objectMapper.readTree(response.body());
    JsonNode result = root.path("results").path(0);
    boolean flagged = result.path("flagged").asBoolean(false);
    if (!flagged) {
      // 任一类别分数超过阈值也判定为敏感
      double threshold = config.getThreshold() == null ? 0.5 : config.getThreshold().doubleValue();
      Map<String, Double> catMap =
          objectMapper.convertValue(
              result.path("category_scores"),
              new tools.jackson.core.type.TypeReference<Map<String, Double>>() {});
      String topCategory = null;
      double topScore = 0;
      for (Map.Entry<String, Double> entry : catMap.entrySet()) {
        double score = entry.getValue() == null ? 0 : entry.getValue();
        if (score > topScore) {
          topScore = score;
          topCategory = entry.getKey();
        }
        if (score >= threshold) {
          flagged = true;
        }
      }
      if (flagged) {
        return new ModerationResult(
            true, "内容审查未通过，最高风险类别: " + topCategory + " (score=" + topScore + ")");
      }
    }
    return flagged ? new ModerationResult(true, "内容审查未通过") : new ModerationResult(false, null);
  }

  /** 分页查询配置。 */
  public IPage<ModerationConfig> list(int page, int size, String name) {
    LambdaQueryWrapper<ModerationConfig> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .like(name != null && !name.isBlank(), ModerationConfig::getName, name)
        .orderByDesc(ModerationConfig::getCreateTime);
    return configMapper.selectPage(Page.of(page, size), wrapper);
  }

  public ModerationConfig create(ModerationConfig config) {
    if (config.getProvider() == null || config.getProvider().isBlank()) {
      config.setProvider("keyword");
    }
    if (config.getThreshold() == null) {
      config.setThreshold(java.math.BigDecimal.valueOf(0.5));
    }
    if (config.getEnabled() == null) {
      config.setEnabled(true);
    }
    configMapper.insert(config);
    return config;
  }

  public ModerationConfig update(Long id, ModerationConfig config) {
    ModerationConfig existing = configMapper.selectById(id);
    if (existing == null) {
      throw new IllegalArgumentException("审查配置不存在: " + id);
    }
    existing.setName(config.getName());
    existing.setProvider(config.getProvider());
    existing.setApiKey(config.getApiKey());
    existing.setApiUrl(config.getApiUrl());
    existing.setThreshold(config.getThreshold());
    existing.setKeywords(config.getKeywords());
    existing.setEnabled(config.getEnabled());
    existing.setRemark(config.getRemark());
    configMapper.updateById(existing);
    return existing;
  }

  public void delete(Long id) {
    configMapper.deleteById(id);
  }
}
