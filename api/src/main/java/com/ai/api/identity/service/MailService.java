package com.ai.api.identity.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.identity.config.AuthProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件服务
 *
 * <p>依据 design.md D7：异步发送（{@link Async}）密码重置邮件。
 *
 * <p>注意：MVP 阶段使用 Spring Mail 同步发送（已通过 {@link Async} 异步执行），后续可平滑迁移 到 RabbitMQ 死信队列。
 *
 * @author add-user-auth
 */
@Slf4j
@Service
public class MailService {

  private final JavaMailSender mailSender;
  private final AuthProperties authProperties;

  public MailService(JavaMailSender mailSender, AuthProperties authProperties) {
    this.mailSender = mailSender;
    this.authProperties = authProperties;
  }

  /**
   * 异步发送密码重置邮件。
   *
   * <p>异常处理：原实现仅 catch {@code MessagingException} 静默吞掉，导致 SMTP 凭据错误时上层 误判发送成功。 现将 {@link
   * MailAuthenticationException}（SMTP 认证失败）、{@link MailSendException} （SMTP 拒绝/连接失败）转换为 {@link
   * BusinessException} 抛出，由 {@code AuthService.forgotPassword} 的调用方 GlobalExceptionHandler
   * 捕获并返回前端，便于用户感知 "邮件未发送"状态。
   *
   * @param toEmail 收件人邮箱
   * @param resetUrl 重置链接
   */
  @Async
  public void sendPasswordResetMail(String toEmail, String resetUrl) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(authProperties.getMail().getFrom());
      helper.setTo(toEmail);
      helper.setSubject("【AI 开放平台】密码重置链接");
      String content = buildResetMailContent(resetUrl);
      helper.setText(content, true);
      mailSender.send(message);
      log.info("[sendPasswordResetMail][to={}, url={}]", toEmail, resetUrl);
    } catch (MailAuthenticationException e) {
      // SMTP 认证失败：用户名/密码错误或应用专用密码缺失
      log.error("[sendPasswordResetMail][SMTP 认证失败：to={}, error={}]", toEmail, e.getMessage());
      throw new BusinessException(ErrorCode.AUTH_MAIL_SEND_FAILED, "SMTP 认证失败，请检查邮箱配置");
    } catch (MailSendException e) {
      // SMTP 拒收/连接失败：收件地址无效、端口/协议不匹配、发送频率受限等
      log.error("[sendPasswordResetMail][SMTP 发送失败：to={}, error={}]", toEmail, e.getMessage());
      throw new BusinessException(ErrorCode.AUTH_MAIL_SEND_FAILED, "邮件发送失败：" + e.getMessage());
    } catch (MessagingException e) {
      // MimeMessage 构造失败（极少见）
      log.error("[sendPasswordResetMail][消息构造失败：to={}, error={}]", toEmail, e.getMessage());
      throw new BusinessException(ErrorCode.AUTH_MAIL_SEND_FAILED, "邮件构造失败");
    } catch (Exception e) {
      // 兜底：包括 NullPointerException（JavaMailSender 未初始化）、ConnectException 等
      log.error("[sendPasswordResetMail][未知异常：to={}, error={}]", toEmail, e.getMessage());
      throw new BusinessException(ErrorCode.AUTH_MAIL_SEND_FAILED, "邮件发送异常：" + e.getMessage());
    }
  }

  private String buildResetMailContent(String resetUrl) {
    return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: 'Helvetica Neue', Arial, sans-serif; color: #333;">
                  <h2>密码重置</h2>
                  <p>您收到此邮件是因为有人请求重置您在 AI 开放平台的密码。</p>
                  <p>请点击下方链接重置密码（30 分钟内有效）：</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 20px;background:#409EFF;color:#fff;text-decoration:none;border-radius:4px;">立即重置密码</a></p>
                  <p>如非本人操作，请忽略此邮件。</p>
                  <hr>
                  <p style="font-size:12px;color:#999;">本邮件由系统自动发送，请勿回复。</p>
                </body>
                </html>
                """
        .formatted(resetUrl);
  }
}
