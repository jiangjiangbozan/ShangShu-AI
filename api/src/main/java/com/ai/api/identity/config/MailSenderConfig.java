package com.ai.api.identity.config;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 邮件发送 Bean 显式配置。
 *
 * <p>默认情况下 {@code JavaMailSender} 由 spring-boot-mail 的 {@code MailSenderAutoConfiguration} 装配，且受
 * {@code @ConditionalOnProperty("spring.mail.host")} 控制。两个副作用：
 *
 * <ol>
 *   <li>IDE 静态分析无法索引用 {@code ${SMTP_HOST:...}} 占位符注入的值，误报"找不到 JavaMailSender 类型的 Bean"；
 *   <li>环境未设置 SMTP 时该 Bean 不存在，{@code MailService} 构造注入直接启动失败。
 * </ol>
 *
 * <p>此处显式声明 Bean，{@code MailSenderAutoConfiguration} 因自身 {@code @ConditionalOnMissingBean}
 * 自动退让，不会造成 Bean 冲突。参数仍读取 {@code spring.mail.*}（由 {@code SMTP_*} 环境变量注入）， 保证功能一致且 Bean 始终存在。
 */
@Configuration
public class MailSenderConfig {

  @Value("${spring.mail.host:}")
  private String host;

  @Value("${spring.mail.port:587}")
  private int port;

  @Value("${spring.mail.username:}")
  private String username;

  @Value("${spring.mail.password:}")
  private String password;

  @Value("${spring.mail.properties.mail.smtp.auth:true}")
  private String smtpAuth;

  @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
  private String starttlsEnable;

  @Bean
  public JavaMailSender javaMailSender() {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(host);
    sender.setPort(port);
    sender.setUsername(username);
    sender.setPassword(password);
    Properties props = sender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", smtpAuth);
    props.put("mail.smtp.starttls.enable", starttlsEnable);
    return sender;
  }
}
