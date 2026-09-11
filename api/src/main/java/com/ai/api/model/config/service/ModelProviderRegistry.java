package com.ai.api.model.config.service;

import com.ai.api.data.entity.SysModelConfig;
import com.ai.api.data.mapper.SysModelConfigMapper;
import com.ai.api.model.config.event.ModelConfigChangedEvent;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 模型 Provider 动态注册表
 *
 * <p>依据 design.md D4：管理所有动态注册的模型配置，监听 {@link ModelConfigChangedEvent} 并更新注册表。
 *
 * <p>对齐 Dify 的 model instance 粒度：key 为 <b>modelId</b>（而非 provider），同一 provider 下可注册多个模型 （如
 * deepseek-chat 与 deepseek-v3）。ChatClient 的实际创建由 {@code ChatClientFactory} 按 modelId 热建并缓存。
 *
 * <p>使用 {@link ConcurrentHashMap} 保证线程安全。配置变更时全量重载（模型数量少，简单且可覆盖 modelId 变更场景）。
 *
 * @author add-chat-module
 */
@Slf4j
@Component
public class ModelProviderRegistry {

  private final SysModelConfigMapper configMapper;

  /** 线程安全的配置注册表：modelId（小写） -> 模型配置 */
  private final ConcurrentHashMap<String, SysModelConfig> configMap = new ConcurrentHashMap<>();

  public ModelProviderRegistry(SysModelConfigMapper configMapper) {
    this.configMapper = configMapper;
  }

  /** 全量重载所有启用的模型配置（启动或任一配置变更后调用，保证与库一致）。 */
  private void reloadAll() {
    var enabledConfigs = configMapper.selectAllEnabled();
    configMap.clear();
    for (SysModelConfig config : enabledConfigs) {
      configMap.put(config.getModelId().toLowerCase(), config);
    }
    log.info("[ModelProviderRegistry][reload][已加载 {} 个启用的动态模型配置]", enabledConfigs.size());
  }

  /** 应用启动时预加载所有已启用的模型配置。 */
  @PostConstruct
  public void init() {
    try {
      reloadAll();
    } catch (Exception e) {
      log.warn(
          "[ModelProviderRegistry][init][预加载模型配置失败，动态配置将在表就绪后通过事件加载] reason: {}", e.getMessage());
    }
  }

  /** 监听模型配置变更事件，重建注册表（modelId 可能变更，全量重载最简单可靠）。 */
  @EventListener
  public void onConfigChanged(ModelConfigChangedEvent event) {
    log.info(
        "[ModelProviderRegistry][onConfigChanged][provider({}) modelId({}) action({})]",
        event.getProvider(),
        event.getModelId(),
        event.getAction());
    try {
      reloadAll();
    } catch (Exception e) {
      log.warn("[ModelProviderRegistry][onConfigChanged][重载失败] reason: {}", e.getMessage());
    }
  }

  /**
   * 获取指定 modelId 的模型配置。
   *
   * @param modelId 模型标识（如 deepseek-chat / deepseek-v3）
   * @return 模型配置，未注册返回 null
   */
  public SysModelConfig getConfig(String modelId) {
    if (modelId == null) return null;
    return configMap.get(modelId.toLowerCase());
  }

  /** 获取所有已注册的 modelId → 配置映射（不可变副本）。 */
  public Map<String, SysModelConfig> getAll() {
    return Map.copyOf(configMap);
  }

  /** 检查 modelId 是否已注册（存在且启用）。 */
  public boolean isRegistered(String modelId) {
    return modelId != null && configMap.containsKey(modelId.toLowerCase());
  }
}
