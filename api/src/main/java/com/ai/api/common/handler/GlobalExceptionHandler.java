package com.ai.api.common.handler;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理
 *
 * <p>依据《企业Java后端开发规范.md》§5.5：统一异常处理，禁止异常裸奔到前端。处理策略：
 *
 * <ul>
 *   <li>{@link BusinessException}：业务异常，使用其携带的 code
 *   <li>{@link MethodArgumentNotValidException} / {@link BindException}：参数校验失败，400
 *   <li>{@link MethodArgumentTypeMismatchException}：参数类型不匹配，400
 *   <li>{@link Exception}：兜底，500
 * </ul>
 *
 * @author bootstrap-project
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 处理业务异常。
   *
   * @param ex 异常
   * @param request 请求
   * @return Result
   */
  @ExceptionHandler(BusinessException.class)
  public Result<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
    log.warn(
        "[handleBusinessException][uri({}) code({}) message({})]",
        request.getRequestURI(),
        ex.getCode(),
        ex.getMessage());
    return Result.fail(ex.getCode(), ex.getMessage());
  }

  /**
   * 处理 @RequestBody 参数校验失败。
   *
   * @param ex 异常
   * @param request 请求
   * @return Result
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Result<Void> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    log.warn(
        "[handleMethodArgumentNotValid][uri({}) errors({})]", request.getRequestURI(), message);
    return Result.fail(400, "参数校验失败：" + message);
  }

  /**
   * 处理表单参数校验失败。
   *
   * @param ex 异常
   * @param request 请求
   * @return Result
   */
  @ExceptionHandler(BindException.class)
  public Result<Void> handleBindException(BindException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
    log.warn("[handleBindException][uri({}) errors({})]", request.getRequestURI(), message);
    return Result.fail(400, "参数校验失败：" + message);
  }

  /**
   * 处理参数类型不匹配。
   *
   * @param ex 异常
   * @param request 请求
   * @return Result
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public Result<Void> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    log.warn(
        "[handleMethodArgumentTypeMismatch][uri({}) name({}) value({})]",
        request.getRequestURI(),
        ex.getName(),
        ex.getValue());
    return Result.fail(400, "参数类型不匹配：" + ex.getName());
  }

  /**
   * 兜底异常处理。
   *
   * @param ex 异常
   * @param request 请求
   * @return ResponseEntity
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> handleException(Exception ex, HttpServletRequest request) {
    log.error("[handleException][uri({}) error({})]", request.getRequestURI(), ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(500, "系统内部错误"));
  }

  /**
   * 处理 Spring Security 认证异常（add-user-auth change）。
   *
   * <p>理论上 JWT 过滤器已通过 {@link com.ai.api.identity.security.AuthEntryPoint} 处理，此处作为 Controller
   * 层的兜底，确保异常不会以 500 形式暴露。
   *
   * @param ex 异常
   * @param request 请求
   * @return Result
   */
  @ExceptionHandler(AuthenticationException.class)
  public Result<Void> handleAuthenticationException(
      AuthenticationException ex, HttpServletRequest request) {
    log.warn(
        "[handleAuthenticationException][uri({}) error({})]",
        request.getRequestURI(),
        ex.getMessage());
    return Result.fail(401, "未认证");
  }

  /**
   * 处理 Spring Security 权限不足异常（add-user-auth change）。
   *
   * @param ex 异常
   * @param request 请求
   * @return Result
   */
  @ExceptionHandler(AccessDeniedException.class)
  public Result<Void> handleAccessDeniedException(
      AccessDeniedException ex, HttpServletRequest request) {
    log.warn(
        "[handleAccessDeniedException][uri({}) error({})]",
        request.getRequestURI(),
        ex.getMessage());
    return Result.fail(403, "权限不足");
  }
}
