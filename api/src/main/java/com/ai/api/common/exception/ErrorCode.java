package com.ai.api.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举
 *
 * <p>依据《企业Java后端开发规范.md》§5.5：错误码按模块分段，命名格式为 {@code BUSINESS_OBJECT_SCENARIO}（如
 * SESSION_NOT_FOUND、DOC_DUPLICATE）。
 *
 * <p>模块分段约定（code 段）：
 *
 * <ul>
 *   <li>1-1xx：通用 common 模块
 *   <li>2-2xx：data 数据层
 *   <li>3-3xx：model 模型层
 *   <li>4-4xx：chat 对话
 *   <li>5-5xx：rag 知识库
 *   <li>6-6xx：agent 任务编排
 *   <li>7-7xx：customer-service 客服
 *   <li>8-8xx：data-analysis 数据分析
 *   <li>9-9xx：identity 用户认证（add-user-auth change）
 * </ul>
 *
 * <p>每段内部细分：
 *
 * <ul>
 *   <li>x01-x09：NOT_FOUND 类
 *   <li>x10-x19：DUPLICATE 类
 *   <li>x20-x29：FAILED 类
 *   <li>x30-x39：BAD_PARAM 类
 * </ul>
 *
 * @author bootstrap-project
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

  // ============ common 模块（1-1xx） ============
  COMMON_BAD_PARAM("100", "参数校验失败"),
  COMMON_NOT_FOUND("101", "资源不存在"),
  COMMON_INTERNAL_ERROR("199", "系统内部错误"),

  // ============ data 数据层（2-2xx） ============
  MODEL_CONFIG_NOT_FOUND("201", "模型配置不存在"),
  MODEL_CONFIG_DUPLICATE("210", "模型配置已存在"),
  MODEL_CONFIG_FAILED("220", "模型配置操作失败"),
  LLM_LOG_NOT_FOUND("202", "LLM 日志不存在"),

  // ============ model 模型层（3-3xx） ============
  MODEL_PING_BAD_PARAM("330", "模型调用参数错误：modelId 与 prompt 不能为空"),
  MODEL_PING_FAILED("320", "模型调用失败"),
  MODEL_NOT_ENABLED("331", "模型未启用"),
  MODEL_API_KEY_MISSING("332", "模型 API Key 未配置"),

  // ============ chat 对话（4-4xx） ============
  SESSION_NOT_FOUND("401", "会话不存在"),

  // ============ identity 用户认证（9-9xx，add-user-auth change） ============
  AUTH_USER_NOT_FOUND("901", "用户不存在"),
  AUTH_USER_EXISTS("910", "用户名或邮箱已被注册"),
  AUTH_EMAIL_EXISTS("911", "邮箱已被注册"),
  AUTH_USERNAME_EXISTS("912", "用户名已被注册"),
  AUTH_BAD_CREDENTIALS("920", "账号或密码错误"),
  AUTH_ACCOUNT_LOCKED("921", "账户已锁定，请稍后重试"),
  AUTH_ACCOUNT_DISABLED("922", "账户已禁用"),
  AUTH_TOKEN_INVALID("930", "Token 无效"),
  AUTH_TOKEN_EXPIRED("931", "Token 已过期"),
  AUTH_RESET_TOKEN_INVALID("932", "重置链接已失效"),
  AUTH_CSRF_FAILED("933", "CSRF 校验失败"),
  AUTH_PASSWORD_WEAK("934", "密码强度不足"),
  AUTH_PASSWORD_MISMATCH("935", "两次密码不一致"),
  AUTH_UNAUTHORIZED("940", "未认证"),
  AUTH_FORBIDDEN("941", "权限不足"),
  AUTH_MAIL_SEND_FAILED("925", "邮件发送失败"),
  AUTH_OAUTH_FAILED("936", "第三方登录失败，请稍后重试"),
  AUTH_OAUTH_STATE_INVALID("937", "第三方登录授权状态无效或已过期"),
  // RBAC（add-rbac-management，沿用 identity 9xx 段）
  RBAC_ROLE_NOT_FOUND("950", "角色不存在"),
  RBAC_ROLE_CODE_DUPLICATE("951", "角色编码已存在"),
  RBAC_PERMISSION_NOT_FOUND("952", "权限不存在"),
  RBAC_PERMISSION_CODE_DUPLICATE("953", "权限编码已存在"),
  RBAC_PERMISSION_IN_USE("954", "权限仍被引用，无法删除"),
  RBAC_SUPER_ROLE_PROTECTED("956", "超级管理员角色受保护，禁止该操作"),
  RBAC_LAST_SUPER_ROLE("957", "无法移除最后一名超级管理员"),

  // ============ agent 任务编排（6-6xx，add-agent-runtime） ============
  AGENT_NOT_FOUND("601", "runtime not found"),
  AGENT_INVALID_STATE_TRANSITION("630", "非法状态转移"),
  AGENT_HEARTBEAT_TIMEOUT("620", "runtime heartbeat timeout"),
  AGENT_CONTEXT_CORRUPTED("621", "context snapshot corrupted"),
  SKILL_NOT_FOUND("602", "skill not registered"),
  SKILL_VERSION_NOT_FOUND("603", "skill version not found"),
  SKILL_INVOKE_FAILED("622", "skill invoke failed"),
  SKILL_NOT_IMPLEMENTED("623", "skill type not supported in M4"),
  WORKFLOW_NOT_FOUND("604", "workflow not found"),
  WORKFLOW_DAG_INVALID("631", "workflow dag invalid"),
  WORKFLOW_NODE_TIMEOUT("624", "workflow node timeout"),
  MEMORY_COMPRESS_FAILED("625", "memory compress failed"),
  REACT_MAX_ITERATIONS("626", "max iterations reached"),
  REACT_INVALID_LLM_OUTPUT("627", "invalid llm output"),
  AGENT_STRATEGY_NOT_SUPPORTED("632", "agent strategy not supported"),
  AGENT_PAUSE_NOT_FOUND("633", "no active pause state"),
  AGENT_PAUSE_ALREADY_EXISTS("634", "pause state already exists"),

  // ============ voice 语音交互（7-6xx） ============
  VOICE_STT_FAILED("760", "speech to text failed"),
  VOICE_TTS_FAILED("761", "text to speech failed"),

  // ============ character-card 角色卡（7-7xx） ============
  CHARACTER_CARD_NOT_FOUND("701", "character card not found"),
  CHARACTER_CARD_NAME_DUPLICATE("702", "character card name duplicate"),

  // ============ data-analysis 数据分析（8-8xx，add-data-analysis） ============
  DATA_ANALYSIS_MODEL_UNAVAILABLE("820", "分析模型不可用"),
  DATA_ANALYSIS_SQL_INVALID("830", "无法生成或执行合法查询"),
  DATA_ANALYSIS_REPORT_FAILED("821", "报告生成失败"),
  ;

  /** 错误码 */
  private final String code;

  /** 错误消息 */
  private final String message;

  /**
   * 转换为整数错误码（用于 Result.fail）。
   *
   * @return Integer
   */
  public Integer asInt() {
    return Integer.parseInt(this.code);
  }
}
