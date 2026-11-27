package com.uit.dormmanagement.configs;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.dtos.response.invoice.VietQrInfoResponse;
import com.uit.dormmanagement.services.VietQrPayloadService;
import com.uit.dormmanagement.services.impl.VietQrPayloadServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VietQrConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(VietQrConfig.class, VietQrPayloadServiceImpl.class));

  @Test
  @DisplayName("Empty app.vietqr defaults bind cleanly and VietQrPayloadService fails closed")
  void emptyDefaults_shouldBindAndFailClosed() {
    contextRunner
        .withPropertyValues(
            "app.vietqr.bank-bin=",
            "app.vietqr.bank-name=",
            "app.vietqr.account-number=",
            "app.vietqr.account-name=",
            "app.vietqr.transfer-prefix=KTX")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(VietQrProperties.class);
              assertThat(context).hasSingleBean(VietQrPayloadService.class);

              VietQrProperties props = context.getBean(VietQrProperties.class);
              assertThat(props.getTransferPrefix()).isEqualTo("KTX");
              assertThat(props.toString()).contains("configured=false");

              VietQrPayloadService service = context.getBean(VietQrPayloadService.class);
              assertThat(service.buildForPayment("INV-202610-001", new BigDecimal("1500000")))
                  .isEmpty();
            });
  }

  @Test
  @DisplayName("Configured app.vietqr properties bind and build payload without logging sensitive values")
  void configuredProperties_shouldBindWithoutLoggingSensitiveValues() {
    String fakeAccountNumber = "88881111222233";
    String fakeAccountName = "KTX TEST ACCOUNT SECRET NAME";

    Logger logger = (Logger) LoggerFactory.getLogger("com.uit.dormmanagement");
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      contextRunner
          .withPropertyValues(
              "app.vietqr.bank-bin=970436",
              "app.vietqr.bank-name=",
              "app.vietqr.account-number=" + fakeAccountNumber,
              "app.vietqr.account-name=" + fakeAccountName,
              "app.vietqr.transfer-prefix=KTXUIT")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                VietQrProperties props = context.getBean(VietQrProperties.class);
                assertThat(props.getBankBin()).isEqualTo("970436");
                assertThat(props.getTransferPrefix()).isEqualTo("KTXUIT");
                assertThat(props.toString())
                    .doesNotContain(fakeAccountNumber)
                    .doesNotContain(fakeAccountName);

                VietQrPayloadService service = context.getBean(VietQrPayloadService.class);
                Optional<VietQrInfoResponse> result =
                    service.buildForPayment("INV-202610-HD-001", new BigDecimal("1500000"));
                assertThat(result).isPresent();
                assertThat(result.get().bankName()).isEqualTo("Vietcombank");
                assertThat(result.get().transferContent()).isEqualTo("KTXUIT INV-202610-HD-001");
              });

      List<String> loggedMessages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(loggedMessages)
          .noneMatch(msg -> msg.contains(fakeAccountNumber) || msg.contains(fakeAccountName));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
