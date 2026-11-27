package com.uit.dormmanagement.schedulers;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceBatchIssuanceResponse;
import com.uit.dormmanagement.services.InvoiceBatchService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyInvoiceIssuanceScheduler {

  private final InvoiceBatchService invoiceBatchService;
  private final Clock clock;

  @Scheduled(
      cron = "${app.scheduler.invoice-issuance-cron:0 0 0 25 * *}",
      zone = "Asia/Ho_Chi_Minh")
  public void issueNextMonthInvoices() {
    LocalDate today = LocalDate.now(clock.withZone(TimeConfig.DORM_ZONE));
    LocalDate nextMonthFirstDay = YearMonth.from(today).plusMonths(1).atDay(1);
    InvoiceBatchIssuanceResponse result = invoiceBatchService.issueMonthlyBatch(nextMonthFirstDay);
    log.info(
        "MonthlyInvoiceIssuanceScheduler completed for billingPeriod={}: eligible={}, issued={}, skippedExisting={}",
        result.billingPeriod(),
        result.eligibleContractCount(),
        result.issuedCount(),
        result.skippedExistingCount());
  }
}
