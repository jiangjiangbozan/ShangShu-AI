package com.ai.api.model.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型配置请求 DTO
 *
 * @author add-chat-module
 */
@Data
public class ModelConfigReqDTO {

  @NotBlank(message = "供应商标识不能为空")
  @Size(max = 32, message = "供应商标识长度不能超过 32 字符")
  private String provider;

  @NotBlank(message = "模型 ID 不能为空")
  @Size(max = 64, message = "模型 ID 长度不能超过 64 字符")
  private String modelId;

  @Size(max = 128, message = "展示名称长度不能超过 128 字符")
  private String displayName;

  /** API Key（写入时接受，不返回明文；留空表示不修改） */
  private String apiKey;

  @Size(max = 256, message = "Base URL 长度不能超过 256 字符")
  private String baseUrl;

  /** 是否启用：1=是，0=否 */
  private Integer enabled;

  /** 模型参数 JSON */
  @Size(max = 2048, message = "参数 JSON 长度不能超过 2048 字符")
  private String parameters;

  /** 排序权重 */
  private Integer sortOrder;
}
