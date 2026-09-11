package com.ai.api.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.owasp.validator.html.AntiSamy;
import org.owasp.validator.html.CleanResults;
import org.owasp.validator.html.Policy;
import org.owasp.validator.html.PolicyException;
import org.owasp.validator.html.ScanException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * XSS 过滤器（AntiSamy）
 *
 * <p>依据 design.md D6：对请求体中标注 {@code @XssProtect} 的字段执行 AntiSamy 清洗。
 *
 * <p>当前 MVP 实现采用简化的全字段清洗（在 {@code RequestBodyAdvice} 中实现更精确），后续可 通过 AOP + 注解切换。本过滤器仅作为兜底防护，对常见 XSS
 * 载荷进行拦截。
 *
 * <p>AntiSamy Policy 使用 {@code antisamy-slashdot.xml}，适合一般用户输入场景。
 *
 * @author add-user-auth
 */
@Component
public class XssFilter extends OncePerRequestFilter {
  // 初始化 AntiSamy 实例
  private final AntiSamy antiSamy;

  public XssFilter() throws PolicyException {
    // AntiSamy 内置 slashdot policy（适合一般用户输入，限制严格）
    Policy policy =
        Policy.getInstance(
            Objects.requireNonNull(getClass().getResourceAsStream("/antisamy-slashdot.xml")));
    this.antiSamy = new AntiSamy(policy);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 使用包装器对请求参数做清洗
    XssRequestWrapper wrappedRequest = new XssRequestWrapper(request, this::cleanXss);
    filterChain.doFilter(wrappedRequest, response);
  }

  /**
   * 清洗输入字符串，移除 XSS 载荷。
   *
   * @param value 原始输入
   * @return 清洗后字符串，若为 null 则原样返回
   */
  String cleanXss(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    try {
      CleanResults results = antiSamy.scan(value);
      return results.getCleanHTML();
    } catch (ScanException | PolicyException e) {
      // 清洗失败则移除所有尖括号字符，保守处理
      return value.replace("<", "&lt;").replace(">", "&gt;");
    }
  }
}
