package com.ai.api.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一响应结果封装
 *
 * <p>依据《企业Java后端开发规范.md》§3.2：所有 Controller 返回统一使用 {@code Result<T>}， 禁止直接返回 POJO/DO/VO 裸对象。该规范要求：
 *
 * <ul>
 *   <li>{@code code}：业务状态码（0=成功，非 0=失败）
 *   <li>{@code message}：用户可见提示
 *   <li>{@code data}：业务数据
 *   <li>{@code traceId}：链路追踪 ID（从 MDC 自动填充）
 * </ul>
 *
 * @author bootstrap-project
 */
@Data
@Schema(description = "统一响应结果")
public class Result<T> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 成功状态码 */
  public static final Integer CODE_SUCCESS = 0;

  /** 失败状态码（通用） */
  public static final Integer CODE_FAIL = 500;

  @Schema(description = "业务状态码：0=成功，非 0=失败", example = "0")
  private Integer code;

  @Schema(description = "提示信息", example = "success")
  private String message;

  @Schema(description = "业务数据")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private T data;

  @Schema(description = "链路追踪 ID")
  private String traceId;

  /** 构造私有化，强制使用工厂方法 */
  private Result() {}

  /**
   * 成功返回（无数据）
   *
   * @param <T> 数据类型
   * @return Result
   */
  public static <T> Result<T> success() {
    return success(null);
  }

  /**
   * 成功返回（带数据）
   *
   * @param data 业务数据
   * @param <T> 数据类型
   * @return Result
   */
  public static <T> Result<T> success(T data) {
    Result<T> result = new Result<>();
    result.code = CODE_SUCCESS;
    result.message = "success";
    result.data = data;
    result.traceId = MDC.get("traceId");
    return result;
  }

  /**
   * 失败返回（指定错误码）
   *
   * @param code 错误码
   * @param message 提示
   * @param <T> 数据类型
   * @return Result
   */
  public static <T> Result<T> fail(Integer code, String message) {
    Result<T> result = new Result<>();
    result.code = code;
    result.message = message;
    result.traceId = MDC.get("traceId");
    return result;
  }

  /**
   * 失败返回（仅消息，使用通用失败码）
   *
   * @param message 提示
   * @param <T> 数据类型
   * @return Result
   */
  public static <T> Result<T> fail(String message) {
    return fail(CODE_FAIL, message);
  }

  /**
   * 判断是否成功
   *
   * @return boolean
   */
  public boolean isSuccess() {
    return CODE_SUCCESS.equals(this.code);
  }
}
