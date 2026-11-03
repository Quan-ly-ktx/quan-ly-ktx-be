package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.services.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private JavaMailSender mailSender;

  @Value("${spring.mail.username:no-reply@dorm.vn}")
  private String fromEmail;

  public EmailServiceImpl() {}

  public EmailServiceImpl(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Override
  @Async
  public void sendForgotPasswordOtp(String toEmail, String fullName, String otp) {
    log.info("Sending forgot password OTP email to recipient: {}", toEmail);
    String subject = "KTX Hub - Yêu cầu khôi phục mật khẩu";
    String title = "Mã xác thực khôi phục mật khẩu";
    String messageBody =
        "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản KTX Hub của bạn. Dưới đây là mã OTP xác thực (hiệu lực trong 5 phút):";

    String htmlContent = buildOtpHtmlContent(fullName, title, messageBody, otp);
    sendHtmlEmail(toEmail, subject, htmlContent);
  }

  private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
    if (mailSender == null) {
      log.warn("JavaMailSender is not configured. Email to {} was not sent", toEmail);
      return;
    }
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

      try {
        helper.setFrom(fromEmail, "KTX Hub");
      } catch (UnsupportedEncodingException e) {
        log.warn("Failed to set personal name on sender, falling back to raw address", e);
        helper.setFrom(fromEmail);
      }

      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      mailSender.send(mimeMessage);
      log.info("Successfully sent email to: {}", toEmail);
    } catch (MessagingException | RuntimeException e) {
      log.error("Failed to send email to: {}. Error: {}", toEmail, e.getMessage(), e);
    }
  }

  private String buildOtpHtmlContent(
      String fullName, String title, String messageBody, String otp) {
    String safeFullName = HtmlUtils.htmlEscape(fullName != null ? fullName : "Quý khách");
    String safeTitle = HtmlUtils.htmlEscape(title);
    String safeMessageBody = HtmlUtils.htmlEscape(messageBody);
    String safeOtp = HtmlUtils.htmlEscape(otp);

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <style>
            body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f6f9; margin: 0; padding: 20px; }
            .container { max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 32px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
            .header { text-align: center; border-bottom: 2px solid #e2e8f0; padding-bottom: 16px; margin-bottom: 24px; }
            .header h2 { color: #1e293b; margin: 0; font-size: 22px; }
            .content { color: #475569; font-size: 15px; line-height: 1.6; }
            .otp-box { background: #f8fafc; border: 2px dashed #6366f1; border-radius: 8px; text-align: center; padding: 18px; margin: 24px 0; }
            .otp-code { font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #4f46e5; }
            .footer { margin-top: 32px; font-size: 12px; color: #94a3b8; text-align: center; border-top: 1px solid #f1f5f9; padding-top: 16px; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header">
              <h2>KTX Hub / PDMS</h2>
            </div>
            <div class="content">
              <p>Xin chào <strong>%s</strong>,</p>
              <p>%s</p>
              <div class="otp-box">
                <div class="otp-code">%s</div>
              </div>
              <p>Mã này có hiệu lực trong vòng <strong>5 phút</strong>. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</p>
              <p>Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email.</p>
            </div>
            <div class="footer">
              <p>&copy; KTX Hub - Hệ thống Quản lý Ký túc xá Tư nhân & Sleepbox</p>
            </div>
          </div>
        </body>
        </html>
        """
        .formatted(safeFullName, safeMessageBody, safeOtp);
  }
}
