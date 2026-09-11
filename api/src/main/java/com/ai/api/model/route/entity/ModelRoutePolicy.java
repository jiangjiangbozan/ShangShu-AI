package com.ai.api.model.route.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 模型路由 / 降级策略配置（A5） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_model_route_policy")
public class ModelRoutePolicy extends BaseDO {

  /** 主模型 ID */
  private String modelId;

  /** 降级模型 ID */
  private String fallbackModelId;

  /** 降级优先级（小者优先，1 最高） */
  private Integer priority;

  /** 状态：active / disabled */
  private String status;

  private String remark;
}
