package com.ai.api.common.config;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Jackson 序列化配置（Spring Boot 4 + Jackson 3）
 *
 * <p>依据《企业Java后端开发规范.md》§3.6：统一日期格式、Long 转字符串避免前端精度丢失。
 *
 * <p>Spring Boot 4 起，Jackson 2 已弃用，统一使用 Jackson 3（{@code tools.jackson} 包）。 Jackson 3 将原 {@code
 * jackson-datatype-jsr310} 模块合并到 {@code jackson-databind} 内部， 包路径由 {@code
 * tools.jackson.datatype.jsr310.*} 变为 {@code tools.jackson.databind.ext.javatime.*}， 且由 {@link
 * tools.jackson.databind.ext.javatime.JavaTimeInitializer} 通过 SPI 自动注册。
 *
 * <p>自动配置的 {@link tools.jackson.databind.json.JsonMapper.Builder} 可通过 {@link
 * JsonMapperBuilderCustomizer} Bean 进行定制。
 *
 * @author bootstrap-project
 */
@Configuration
public class JacksonConfig {

  /** 统一日期时间格式 */
  public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

  /** 统一日期格式 */
  public static final String DATE_FORMAT = "yyyy-MM-dd";

  /**
   * 自定义 Jackson 行为。
   *
   * <p>注：Jackson 3 中 JavaTimeModule 已由 SPI 自动注册，此处通过自定义模块覆盖 LocalDateTime / LocalDate 的默认格式（统一为
   * {@link #DATE_TIME_FORMAT}）。
   *
   * @return {@link JsonMapperBuilderCustomizer} 定制器
   */
  @Bean
  public JsonMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> {
      // 时区
      builder.defaultTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
      // 日期格式（Date 类型）
      builder.defaultDateFormat(new SimpleDateFormat(DATE_TIME_FORMAT, Locale.CHINA));
      // LocalDateTime / LocalDate 自定义格式
      SimpleModule dateTimeModule = new SimpleModule();
      DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
      DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
      dateTimeModule.addSerializer(
          LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
      dateTimeModule.addDeserializer(
          LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));
      dateTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
      dateTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));
      builder.addModule(dateTimeModule);
      // Long / BigInteger 转字符串（防止前端 JS 精度丢失，Long 超过 2^53）
      SimpleModule longModule = new SimpleModule();
      longModule.addSerializer(Long.class, ToStringSerializer.instance);
      longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
      longModule.addSerializer(BigInteger.class, ToStringSerializer.instance);
      builder.addModule(longModule);
    };
  }
}
