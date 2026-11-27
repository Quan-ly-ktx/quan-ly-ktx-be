package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.services.impl.EmailServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class EmailServiceImplTest {

  @Test
  @DisplayName("sendForgotPasswordOtp masks recipient email and omits OTP across all log paths")
  void sendForgotPasswordOtp_shouldMaskRecipientEmailInLogs() {
    String rawEmail = "recipient.secret@dorm.vn";
    String rawOtp = "849201";

    Logger logger = (Logger) LoggerFactory.getLogger(EmailServiceImpl.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      // 1. No mailSender configured
      EmailServiceImpl noSenderService = new EmailServiceImpl();
      ReflectionTestUtils.setField(noSenderService, "fromEmail", "no-reply@dorm.vn");
      noSenderService.sendForgotPasswordOtp(rawEmail, "Nguyen Van A", rawOtp);

      // 2. Successful send
      JavaMailSender mailSender = mock(JavaMailSender.class);
      MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
      when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
      EmailServiceImpl successService = new EmailServiceImpl(mailSender);
      ReflectionTestUtils.setField(successService, "fromEmail", "no-reply@dorm.vn");
      successService.sendForgotPasswordOtp(rawEmail, "Nguyen Van A", rawOtp);
      verify(mailSender).send(any(MimeMessage.class));

      // 3. Send failure
      JavaMailSender failingSender = mock(JavaMailSender.class);
      when(failingSender.createMimeMessage())
          .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
      doThrow(new MailSendException("SMTP down")).when(failingSender).send(any(MimeMessage.class));
      EmailServiceImpl failService = new EmailServiceImpl(failingSender);
      ReflectionTestUtils.setField(failService, "fromEmail", "no-reply@dorm.vn");
      failService.sendForgotPasswordOtp(rawEmail, "Nguyen Van A", rawOtp);

      List<String> messages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("r***@dorm.vn"));
      assertThat(messages).noneMatch(msg -> msg.contains(rawEmail) || msg.contains(rawOtp));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
