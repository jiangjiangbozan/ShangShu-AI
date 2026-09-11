package com.ai.api.agent.eval.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Agent 评测记录（A3）：人工对一次运行打分/标记。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_agent_eval")
public class AgentEval extends BaseDO {

  private Long runtimeId;

  private Long sessionId;

  private Long agentConfigId;

  private String modelId;

  /** 评测人 */
  private Long userId;

  /** 运行结果: success / failed */
  private String status;

  private String input;

  private String output;

  private Long tokens;

  private Long latencyMs;

  /** 人工评分 1-5 */
  private Integer score;

  /** 评价: good / bad / none */
  private String verdict;

  private String note;
}
