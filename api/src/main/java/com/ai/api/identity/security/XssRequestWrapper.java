package com.ai.api.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.function.Function;
import org.springframework.util.StringUtils;

/**
 * XSS 请求包装器
 *
 * <p>对 {@link HttpServletRequest#getParameter(String)} 与 {@link #getParameterValues(String)}
 * 的返回值应用清洗函数。
 *
 * <p>注意：本包装器仅作用于 form-encoded 参数。对 application/json 请求体，由后续 RequestBodyAdvice 处理。MVP 阶段 form
 * 参数清洗已覆盖主要的查询接口场景。
 *
 * @author add-user-auth
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

  private final Function<String, String> sanitizer;

  public XssRequestWrapper(HttpServletRequest request, Function<String, String> sanitizer) {
    super(request);
    this.sanitizer = sanitizer;
  }

  @Override
  public String[] getParameterValues(String name) {
    String[] values = super.getParameterValues(name);
    if (values == null) {
      return null;
    }
    String[] cleaned = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      cleaned[i] = sanitize(values[i]);
    }
    return cleaned;
  }

  @Override
  public String getParameter(String name) {
    return sanitize(super.getParameter(name));
  }

  private String sanitize(String value) {
    if (!StringUtils.hasText(value)) {
      return value;
    }
    return sanitizer.apply(value);
  }
}
