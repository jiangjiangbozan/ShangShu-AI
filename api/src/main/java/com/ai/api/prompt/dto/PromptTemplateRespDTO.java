package com.ai.api.prompt.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class PromptTemplateRespDTO {

  private Long id;
  private String name;
  private String category;
  private String content;
  private List<String> variables;
  private String modelId;
  private Boolean isActive;
  private Integer version;
  private String description;
  private String createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
