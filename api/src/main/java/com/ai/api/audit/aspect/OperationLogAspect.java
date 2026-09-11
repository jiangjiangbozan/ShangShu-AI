package com.ai.api.audit.aspect;

import com.ai.api.audit.annotation.OperationLog;
import com.ai.api.audit.service.OperationLogService;
import com.ai.api.identity.security.AuthUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

/** 操作审计切面（A6）：拦截 @OperationLog 标注的方法，自动记录操作日志。 */
@Aspect
@Component
public class OperationLogAspect {

  private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

  private final OperationLogService operationLogService;
  private final ObjectMapper objectMapper;

  public OperationLogAspect(OperationLogService operationLogService, ObjectMapper objectMapper) {
    this.operationLogService = operationLogService;
    this.objectMapper = objectMapper;
  }

  @Around("@annotation(operationLog)")
  public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
    Instant start = Instant.now();
    String status = "success";
    String errorMsg = null;
    try {
      return pjp.proceed();
    } catch (Throwable t) {
      status = "fail";
      errorMsg = t.getMessage();
      throw t;
    } finally {
      try {
        var entry = new com.ai.api.audit.entity.OperationLog();
        fillUser(entry);
        fillRequest(entry);
        entry.setModule(operationLog.module());
        entry.setAction(operationLog.action());
        entry.setParams(serializeParams(pjp.getArgs()));
        entry.setStatus(status);
        entry.setErrorMsg(errorMsg);
        entry.setDurationMs(Duration.between(start, Instant.now()).toMillis());
        operationLogService.record(entry);
      } catch (Exception e) {
        log.warn("[audit][切面记录失败忽略: {}]", e.getMessage());
      }
    }
  }

  private void fillUser(com.ai.api.audit.entity.OperationLog entry) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthUserDetails details) {
      entry.setUserId(details.userId());
      entry.setUsername(details.username());
    }
  }

  private void fillRequest(com.ai.api.audit.entity.OperationLog entry) {
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) return;
    HttpServletRequest request = attrs.getRequest();
    entry.setMethod(request.getMethod());
    entry.setPath(request.getRequestURI());
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      entry.setIp(forwarded.split(",")[0].trim());
    } else {
      entry.setIp(request.getRemoteAddr());
    }
  }

  private String serializeParams(Object[] args) {
    if (args == null || args.length == 0) return null;
    try {
      for (Object arg : args) {
        if (arg == null
            || arg instanceof HttpServletRequest
            || arg instanceof jakarta.servlet.http.HttpServletResponse
            || arg instanceof org.springframework.ui.Model
            || arg instanceof org.springframework.validation.BindingResult) {
          continue;
        }
        String json = objectMapper.writeValueAsString(arg);
        if (json == null || json.isBlank() || "null".equals(json)) continue;
        return json.length() > 2000 ? json.substring(0, 2000) : json;
      }
    } catch (Exception e) {
      log.debug("[audit][序列化参数失败忽略: {}]", e.getMessage());
    }
    return null;
  }
}
