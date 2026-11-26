package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.services.InvoiceIssuanceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceIssuanceServiceImpl implements InvoiceIssuanceService {

  private static final Set<InvoiceStatus> UNSETTLED_STATUSES =
      Set.of(InvoiceStatus.DEBT, InvoiceStatus.PENDING_CONFIRMATION);
  private static final DateTimeFormatter BILLING_PERIOD_CODE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMM");
  private static final int DUE_DAY_OF_MONTH = 5;
  private static final int MONTHLY_ISSUANCE_DAY = 25;

  private final InvoiceRepository invoiceRepository;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public boolean hasUnsettledInvoices(UUID tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("tenantId must not be null");
    }
    return invoiceRepository.existsByContractTenantIdAndStatusIn(tenantId, UNSETTLED_STATUSES);
  }

  @Override
  @Transactional
  public Invoice issueMonthlyInvoiceIfAbsent(
      Contract contract, LocalDate billingPeriod, BigDecimal amount) {
    validateContractForIssuance(contract);
    if (billingPeriod == null || billingPeriod.getDayOfMonth() != 1) {
      throw new IllegalArgumentException("billingPeriod must be the 1st day of the month");
    }
    BigDecimal normalizedAmount = validateAndNormalizeVndAmount(amount);

    Optional<Invoice> existing =
        invoiceRepository.findByContractIdAndBillingPeriod(contract.getId(), billingPeriod);
    if (existing.isPresent()) {
      return existing.get();
    }

    String invoiceCode =
        "INV-"
            + billingPeriod.format(BILLING_PERIOD_CODE_FORMAT)
            + "-"
            + contract.getContractCode().trim();

    Invoice invoice =
        Invoice.builder()
            .invoiceCode(invoiceCode)
            .contract(contract)
            .billingPeriod(billingPeriod)
            .amount(normalizedAmount)
            .dueOn(billingPeriod.withDayOfMonth(DUE_DAY_OF_MONTH))
            .status(InvoiceStatus.DEBT)
            .proofFileKey(null)
            .proofSubmittedAt(null)
            .build();

    return invoiceRepository.saveAndFlush(invoice);
  }

  @Override
  @Transactional
  public Optional<Invoice> issueRenewalCatchUpInvoiceIfEligible(
      Contract contract, ContractRenewal renewal) {
    if (contract == null || contract.getId() == null) {
      throw new IllegalArgumentException("contract and contract.id must not be null");
    }
    if (renewal == null) {
      throw new IllegalArgumentException("renewal must not be null");
    }
    if (renewal.getContract() != null && !contract.getId().equals(renewal.getContract().getId())) {
      throw new IllegalArgumentException("renewal does not belong to the supplied contract");
    }

    if (contract.getStatus() != ContractStatus.ACTIVE) {
      return Optional.empty();
    }

    LocalDate today = LocalDate.now(clock.withZone(TimeConfig.DORM_ZONE));
    if (today.getDayOfMonth() < MONTHLY_ISSUANCE_DAY) {
      return Optional.empty();
    }

    YearMonth currentMonth = YearMonth.from(today);
    if (renewal.getPreviousEndOn() == null
        || !currentMonth.equals(YearMonth.from(renewal.getPreviousEndOn()))) {
      return Optional.empty();
    }

    LocalDate nextMonthFirstDay = currentMonth.plusMonths(1).atDay(1);
    if (renewal.getEffectiveFrom() == null
        || !renewal.getEffectiveFrom().equals(nextMonthFirstDay)
        || renewal.getNewEndOn() == null
        || renewal.getNewEndOn().isBefore(nextMonthFirstDay)) {
      return Optional.empty();
    }

    return Optional.of(
        issueMonthlyInvoiceIfAbsent(contract, nextMonthFirstDay, renewal.getMonthlyRent()));
  }

  private static void validateContractForIssuance(Contract contract) {
    if (contract == null || contract.getId() == null) {
      throw new IllegalArgumentException("contract and contract.id must not be null");
    }
    if (contract.getContractCode() == null || contract.getContractCode().isBlank()) {
      throw new IllegalArgumentException("contract.contractCode must not be blank");
    }
  }

  private static BigDecimal validateAndNormalizeVndAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be strictly greater than zero");
    }
    if (amount.stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException("amount must be an integer VND value");
    }
    return amount.setScale(0, RoundingMode.UNNECESSARY);
  }
}
