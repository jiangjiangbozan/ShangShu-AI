package com.ai.api.model.config.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 模型配置变更事件
 *
 * <p>当模型配置发生创建、更新或删除时，由 {@code ModelConfigService} 发布，{@code ModelProviderRegistry} 监听并动态重建 {@link
 * org.springframework.ai.chat.client.ChatClient}。
 *
 * @author add-chat-module
 */
@Getter
public class ModelConfigChangedEvent extends ApplicationEvent {

  /** 供应商标识 */
  private final String provider;

  /** 模型 ID */
  private final String modelId;

  /** 操作类型 */
  private final Action action;

  public ModelConfigChangedEvent(Object source, String provider, String modelId, Action action) {
    super(source);
    this.provider = provider;
    this.modelId = modelId;
    this.action = action;
  }

  public enum Action {
    CREATE,
    UPDATE,
    DELETE
  }
}
