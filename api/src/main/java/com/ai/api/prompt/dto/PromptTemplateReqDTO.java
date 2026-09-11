package com.ai.api.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class PromptTemplateReqDTO {

  @NotBlank(message = "模板名称不能为空")
  @Size(max = 128)
  private String name;

  @Size(max = 64)
  private String category = "chat";

  @NotBlank(message = "模板内容不能为空")
  private String content;

  private List<String> variables;

  private String modelId;

  private Boolean isActive = true;

  @Size(max = 512)
  private String description;
}
