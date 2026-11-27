package com.uit.dormmanagement.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceBatchIssuanceResponse;
import com.uit.dormmanagement.services.InvoiceBatchService;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class MonthlyInvoiceIssuanceSchedulerTest {

  @Mock private InvoiceBatchService invoiceBatchService;

  @Test
  @DisplayName(
      "issueNextMonthInvoices: targets next month at 00:00 Asia/Ho_Chi_Minh UTC boundary and logs summary without PII")
  void issueNextMonthInvoices_usesDormZoneMidnightAndLogsCounts() {
    // 2026-10-24T17:00:00Z == 2026-10-25T00:00:00+07:00 -> next month = 2026-11-01
    Clock oct25DormMidnight =
        Clock.fixed(Instant.parse("2026-10-24T17:00:00Z"), ZoneOffset.UTC);
    MonthlyInvoiceIssuanceScheduler scheduler =
        new MonthlyInvoiceIssuanceScheduler(invoiceBatchService, oct25DormMidnight);

    LocalDate expectedNovPeriod = LocalDate.of(2026, 11, 1);
    when(invoiceBatchService.issueMonthlyBatch(expectedNovPeriod))
        .thenReturn(
            InvoiceBatchIssuanceResponse.builder()
                .billingPeriod(expectedNovPeriod)
                .eligibleContractCount(5)
                .issuedCount(4)
                .skippedExistingCount(1)
                .issuedInvoices(List.of())
                .build());

    Logger logger = (Logger) LoggerFactory.getLogger(MonthlyInvoiceIssuanceScheduler.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);

    try {
      scheduler.issueNextMonthInvoices();

      verify(invoiceBatchService).issueMonthlyBatch(expectedNovPeriod);
      verifyNoMoreInteractions(invoiceBatchService);

      List<String> loggedMessages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(loggedMessages)
          .anySatisfy(
              msg ->
                  assertThat(msg)
                      .contains("2026-11-01")
                      .contains("eligible=5")
                      .contains("issued=4")
                      .contains("skippedExisting=1"));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  @DisplayName(
      "issueNextMonthInvoices: crosses year boundary cleanly on 2026-12-25 Asia/Ho_Chi_Minh")
  void issueNextMonthInvoices_crossesYearBoundary() {
    // 2026-12-24T17:00:00Z == 2026-12-25T00:00:00+07:00 -> next month = 2027-01-01
    Clock dec25DormMidnight =
        Clock.fixed(Instant.parse("2026-12-24T17:00:00Z"), ZoneOffset.UTC);
    MonthlyInvoiceIssuanceScheduler scheduler =
        new MonthlyInvoiceIssuanceScheduler(invoiceBatchService, dec25DormMidnight);

    LocalDate expectedJanPeriod = LocalDate.of(2027, 1, 1);
    when(invoiceBatchService.issueMonthlyBatch(expectedJanPeriod))
        .thenReturn(
            InvoiceBatchIssuanceResponse.builder()
                .billingPeriod(expectedJanPeriod)
                .eligibleContractCount(0)
                .issuedCount(0)
                .skippedExistingCount(0)
                .issuedInvoices(List.of())
                .build());

    scheduler.issueNextMonthInvoices();

    verify(invoiceBatchService).issueMonthlyBatch(expectedJanPeriod);
    verifyNoMoreInteractions(invoiceBatchService);
  }

  @Test
  @DisplayName(
      "issueNextMonthInvoices declares day-25 Asia/Ho_Chi_Minh cron and NO @Transactional")
  void issueNextMonthInvoices_hasExpectedScheduleAndNoOuterTransactional() throws Exception {
    assertThat(MonthlyInvoiceIssuanceScheduler.class.getAnnotation(Transactional.class)).isNull();

    Method method = MonthlyInvoiceIssuanceScheduler.class.getMethod("issueNextMonthInvoices");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.cron()).isEqualTo("${app.scheduler.invoice-issuance-cron:0 0 0 25 * *}");
    assertThat(scheduled.zone()).isEqualTo(TimeConfig.DORM_ZONE.getId());
    assertThat(method.getAnnotation(Transactional.class)).isNull();
  }
}
