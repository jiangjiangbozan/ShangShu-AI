package com.ai.api.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置
 *
 * <p>依据《企业Java后端开发规范.md》§4.7：开发环境允许前端 {@code http://localhost:5173} 跨域访问后端，可通过配置文件自定义。
 *
 * @author bootstrap-project
 */
@Configuration
public class CorsConfig {

  /**
   * 注册 CorsFilter（优先级低于 TraceIdFilter，让 TraceId 先注入）。
   *
   * @param allowedOrigins 允许的源（从配置 {@code ai.cors.allowed-origins} 读取）
   * @return FilterRegistrationBean
   */
  @Bean
  public FilterRegistrationBean<CorsFilter> corsFilterRegistration(
      @Value("${ai.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
          String[] allowedOrigins) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();
    // 允许携带 Cookie
    config.setAllowCredentials(true);
    // 允许所有源（结合 allowCredentials=true 必须用 pattern）
    config.addAllowedOriginPattern("*");
    // 显式允许配置的源
    for (String origin : allowedOrigins) {
      config.addAllowedOrigin(origin);
    }
    // 允许的请求头
    config.addAllowedHeader(CorsConfiguration.ALL);
    // 允许的方法
    config.addAllowedMethod(CorsConfiguration.ALL);
    // 暴露的响应头（前端可读取 X-Trace-Id）
    config.addExposedHeader("X-Trace-Id");
    // 预检缓存
    config.setMaxAge(3600L);
    source.registerCorsConfiguration("/**", config);

    FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new CorsFilter(source));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return bean;
  }
}
