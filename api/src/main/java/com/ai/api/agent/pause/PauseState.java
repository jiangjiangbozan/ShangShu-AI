package com.ai.api.agent.pause;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * Agent 显式暂停状态（add-agent-runner-pipeline）。
 *
 * <p>轻量级暂停点存储，替代 {@code AgentContextManager} 全量快照的暂停场景：
 *
 * <ul>
 *   <li>nextStepIndex：下一步执行序号，resume 后从该步继续
 *   <li>pauseType：{@link #NORMAL} 普通暂停 / {@link #ASK_HUMAN} 人工介入
 *   <li>variables：待确认变量（人工介入时传给人类的上下文）
 *   <li>reply：人工回复内容（RESOLVED 时回填，由 {@code AgentRuntimeService.respond} 注入上下文）
 * </ul>
 */
@Data
@TableName(value = "agent_pause_state", autoResultMap = true)
public class PauseState {

  /** 普通暂停（外部暂停 / 恢复）。 */
  public static final String TYPE_NORMAL = "NORMAL";

  /** 人工介入（ask-human），等待人工确认。 */
  public static final String TYPE_ASK_HUMAN = "ASK_HUMAN";

  /** 等待人工 / 等待恢复。 */
  public static final String STATUS_WAITING = "WAITING";

  /** 已处理（收到人工回复或已恢复执行）。 */
  public static final String STATUS_RESOLVED = "RESOLVED";

  /** 已取消。 */
  public static final String STATUS_CANCELLED = "CANCELLED";

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long runtimeId;

  /** 下一步执行序号（resume 后从该步继续）。 */
  private Integer nextStepIndex;

  /** 暂停类型：NORMAL | ASK_HUMAN。 */
  private String pauseType;

  /** ask-human 场景向人类展示的问题。 */
  private String question;

  /** 待确认变量（人工介入上下文）。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> variables;

  /** 人工回复内容（RESOLVED 时写入，供 resume 回填上下文）。 */
  private String reply;

  /** 状态：WAITING | RESOLVED | CANCELLED。 */
  private String status;

  private LocalDateTime createTime;

  private LocalDateTime updateTime;
}
