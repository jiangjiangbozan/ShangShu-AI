package com.ai.api.memory.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** 记忆响应 DTO */
@Data
public class MemoryRespDTO {

  private Long id;
  private String category;
  private String content;
  private String source;
  private Integer importance;
  private Integer timesUsed;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
