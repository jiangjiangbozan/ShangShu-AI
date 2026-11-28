package com.ai.api.common.exception;

/**
 * 边界校验失败异常（mobile-p0）。
 *
 * <p>复用 {@link BusinessException} 的 Result 机制，但使用 HTTP 400 语义的错误码， 由全局异常处理器转换为 400
 * 响应，避免把参数错误记成业务失败码或数据库异常。
 *
 * @author mobile-p0
 */
public class BadRequestException extends BusinessException {

  /** 参数错误码，与 {@code Result.fail(400, ...)} 保持一致。 */
  public static final int BAD_REQUEST_CODE = 400;

  public BadRequestException(String message) {
    super(BAD_REQUEST_CODE, message);
  }
}
