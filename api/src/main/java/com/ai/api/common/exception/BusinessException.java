package com.ai.api.common.exception;

import java.io.Serial;
import lombok.Getter;

/**
 * 业务异常
 *
 * <p>依据《企业Java后端开发规范.md》§5.5：业务层禁止抛出 RuntimeException，统一使用 {@code BusinessException} + {@link
 * ErrorCode}。Service 层使用模式：
 *
 * <pre>{@code
 * if (config == null) {
 *     throw new BusinessException(ErrorCode.MODEL_CONFIG_NOT_FOUND);
 * }
 * }</pre>
 *
 * @author bootstrap-project
 */
@Getter
public class BusinessException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** 错误码 */
  private final Integer code;

  /**
   * 通过 ErrorCode 构造。
   *
   * @param errorCode ErrorCode 枚举
   */
  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.code = errorCode.asInt();
  }

  /**
   * 通过 ErrorCode + 自定义消息构造。
   *
   * @param errorCode ErrorCode 枚举
   * @param message 自定义消息
   */
  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.code = errorCode.asInt();
  }

  /**
   * 通过自定义 code 与 message 构造。
   *
   * @param code 错误码
   * @param message 错误消息
   */
  public BusinessException(Integer code, String message) {
    super(message);
    this.code = code;
  }
}
