package com.ai.api.model.config.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.data.entity.SysModelConfig;
import com.ai.api.data.mapper.SysModelConfigMapper;
import com.ai.api.model.config.event.ModelConfigChangedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * 模型配置管理 Service
 *
 * <p>依据 spec.md "模型配置管理"：CRUD + 连接测试 + API Key 加密存储 + 动态加载。
 *
 * @author add-chat-module
 */
@Slf4j
@Service
public class ModelConfigService {

  private final SysModelConfigMapper mapper;
  private final StringEncryptor encryptor;
  private final ApplicationEventPublisher eventPublisher;

  /** JSON 校验用 ObjectMapper（线程安全，静态复用）。 */
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  public ModelConfigService(
      SysModelConfigMapper mapper,
      StringEncryptor encryptor,
      ApplicationEventPublisher eventPublisher) {
    this.mapper = mapper;
    this.encryptor = encryptor;
    this.eventPublisher = eventPublisher;
  }

  /** 查询所有模型配置（按 sortOrder 升序）。 */
  public List<SysModelConfig> listConfigs() {
    LambdaQueryWrapper<SysModelConfig> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByAsc(SysModelConfig::getSortOrder);
    return mapper.selectList(wrapper);
  }

  /** 按 ID 查询配置。 */
  public SysModelConfig getConfig(Long id) {
    SysModelConfig config = mapper.selectById(id);
    if (config == null) {
      throw new BusinessException(ErrorCode.MODEL_CONFIG_NOT_FOUND);
    }
    return config;
  }

  /**
   * 创建模型配置。
   *
   * <p>API Key 使用 Jasypt 加密后存入 apiKeyEncrypted 字段。
   */
  @Transactional
  public SysModelConfig createConfig(SysModelConfig config) {
    // 检查唯一性
    checkDuplicate(config.getModelId(), null);

    // 加密 API Key
    if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
      config.setApiKeyEncrypted(encryptor.encrypt(config.getApiKey()));
      config.setApiKey(null); // 不存储明文
    }

    // 默认值
    if (config.getEnabled() == null) {
      config.setEnabled(1);
    }
    if (config.getSortOrder() == null) {
      config.setSortOrder(0);
    }
    normalizeParameters(config);

    mapper.insert(config);
    log.info(
        "[ModelConfigService][createConfig][provider({}) modelId({})]",
        config.getProvider(),
        config.getModelId());

    // 发布变更事件
    eventPublisher.publishEvent(
        new ModelConfigChangedEvent(
            this,
            config.getProvider(),
            config.getModelId(),
            ModelConfigChangedEvent.Action.CREATE));
    return config;
  }

  /** 更新模型配置。 */
  @Transactional
  public SysModelConfig updateConfig(Long id, SysModelConfig config) {
    SysModelConfig existing = getConfig(id);

    // 检查唯一性（排除自身）
    if (config.getModelId() != null && !config.getModelId().equals(existing.getModelId())) {
      checkDuplicate(config.getModelId(), id);
    }

    config.setId(id);
    // 加密新的 API Key
    if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
      config.setApiKeyEncrypted(encryptor.encrypt(config.getApiKey()));
      config.setApiKey(null);
    }
    normalizeParameters(config);

    mapper.updateById(config);
    log.info("[ModelConfigService][updateConfig][id({}) provider({})]", id, existing.getProvider());

    // 发布变更事件
    eventPublisher.publishEvent(
        new ModelConfigChangedEvent(
            this,
            existing.getProvider(),
            existing.getModelId(),
            ModelConfigChangedEvent.Action.UPDATE));
    return mapper.selectById(id);
  }

  /** 删除模型配置（逻辑删除）。 */
  @Transactional
  public void deleteConfig(Long id) {
    SysModelConfig config = getConfig(id);
    mapper.deleteById(id);
    log.info("[ModelConfigService][deleteConfig][id({}) provider({})]", id, config.getProvider());

    eventPublisher.publishEvent(
        new ModelConfigChangedEvent(
            this,
            config.getProvider(),
            config.getModelId(),
            ModelConfigChangedEvent.Action.DELETE));
  }

  /**
   * 规范化模型参数 JSON（列类型为 MySQL JSON）。
   *
   * <p>前端未填写时提交空字符串，直接写入 JSON 列会触发 {@code Data truncation: Invalid JSON text}， 这里统一将空白置为
   * null；非空白则校验为合法 JSON 对象，给出友好报错而非数据库截断错误。
   *
   * @param config 待入库的模型配置
   */
  private void normalizeParameters(SysModelConfig config) {
    String parameters = config.getParameters();
    if (parameters == null || parameters.isBlank()) {
      config.setParameters(null);
      return;
    }
    String trimmed = parameters.trim();
    try {
      JsonNode node = JSON_MAPPER.readTree(trimmed);
      if (!node.isObject()) {
        throw new BusinessException(ErrorCode.COMMON_BAD_PARAM, "模型参数必须为合法 JSON 对象");
      }
      config.setParameters(trimmed);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.COMMON_BAD_PARAM, "模型参数必须为合法 JSON 对象");
    }
  }

  /**
   * 解密 API Key。
   *
   * @param config 模型配置
   * @return 解密后的明文 API Key，如果 apiKeyEncrypted 为空则返回 null
   */
  public String decryptApiKey(SysModelConfig config) {
    if (config.getApiKeyEncrypted() == null || config.getApiKeyEncrypted().isBlank()) {
      return null;
    }
    try {
      return encryptor.decrypt(config.getApiKeyEncrypted());
    } catch (Exception e) {
      log.warn("[ModelConfigService][decryptApiKey][解密失败 id({})]", config.getId(), e);
      return null;
    }
  }

  /** API Key 脱敏：仅显示前 4 位 + **** + 后 4 位。 */
  public String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.length() <= 8) {
      return "****";
    }
    return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
  }

  /**
   * 测试模型 API 连接。
   *
   * <p>使用 RestTemplate 发送 HTTP POST 请求到模型 API 的 chat/completions 端点，验证连接可用性。
   *
   * @param config 模型配置
   * @return 测试结果（success=true/false + 延迟 ms）
   */
  public ModelConnectionTestResult testConnection(SysModelConfig config) {
    String apiKey = decryptApiKey(config);
    if (apiKey == null) {
      return ModelConnectionTestResult.fail("API Key 未配置");
    }
    if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
      return ModelConnectionTestResult.fail("Base URL 未配置");
    }
    if (config.getModelId() == null || config.getModelId().isBlank()) {
      return ModelConnectionTestResult.fail("Model ID 未配置");
    }

    long start = System.currentTimeMillis();
    try {
      RestTemplate restTemplate = new RestTemplate();
      String url = config.getBaseUrl().replaceAll("/+$", "") + "/v1/chat/completions";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(apiKey);

      String body =
          """
          {
            "model": "%s",
            "messages": [{"role": "user", "content": "ping"}],
            "max_tokens": 5
          }
          """
              .formatted(config.getModelId());

      HttpEntity<String> request = new HttpEntity<>(body, headers);
      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.POST, request, String.class);

      long latency = System.currentTimeMillis() - start;
      if (response.getStatusCode().is2xxSuccessful()) {
        log.info(
            "[ModelConfigService][testConnection][provider({}) latency({}ms)]",
            config.getProvider(),
            latency);
        return ModelConnectionTestResult.success(latency, "连接成功");
      }
      return ModelConnectionTestResult.fail("HTTP " + response.getStatusCode().value());
    } catch (Exception e) {
      long latency = System.currentTimeMillis() - start;
      log.warn("[ModelConfigService][testConnection][provider({}) 连接失败]", config.getProvider(), e);
      return ModelConnectionTestResult.fail("连接失败: " + e.getMessage());
    }
  }

  private void checkDuplicate(String modelId, Long excludeId) {
    LambdaQueryWrapper<SysModelConfig> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SysModelConfig::getModelId, modelId);
    if (excludeId != null) {
      wrapper.ne(SysModelConfig::getId, excludeId);
    }
    if (mapper.selectCount(wrapper) > 0) {
      throw new BusinessException(ErrorCode.MODEL_CONFIG_DUPLICATE);
    }
  }

  /** 连接测试结果。 */
  public record ModelConnectionTestResult(boolean success, long latencyMs, String message) {
    public static ModelConnectionTestResult success(long latencyMs, String response) {
      return new ModelConnectionTestResult(true, latencyMs, response);
    }

    public static ModelConnectionTestResult fail(String message) {
      return new ModelConnectionTestResult(false, 0, message);
    }
  }
}
