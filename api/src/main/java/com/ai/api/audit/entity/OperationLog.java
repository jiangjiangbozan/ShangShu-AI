package com.ai.api.audit.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 操作审计日志（A6）。createTime 即操作时间。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_operation_log")
public class OperationLog extends BaseDO {

  private Long userId;

  private String username;

  /** 模块（如 model-config / knowledge / agent） */
  private String module;

  /** 动作（如 create / update / delete / query） */
  private String action;

  private String method;

  private String path;

  private String ip;

  /** 请求参数（截断） */
  private String params;

  /** 结果: success / fail */
  private String status;

  private String errorMsg;

  private Long durationMs;
}
