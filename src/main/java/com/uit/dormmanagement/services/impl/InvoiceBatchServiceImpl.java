package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceBatchIssuanceResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryResponse;
import com.uit.dormmanagement.dtos.response.invoice.PaymentTransactionResponse;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.DataIntegrityViolationClassifier;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.services.InvoiceBatchService;
import com.uit.dormmanagement.services.InvoiceIssuanceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class InvoiceBatchServiceImpl implements InvoiceBatchService {

  private static final int MONTHLY_ISSUANCE_DAY = 25;

  private final ContractRepository contractRepository;
  private final ContractRenewalRepository contractRenewalRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceIssuanceService invoiceIssuanceService;
  private final Clock clock;
  private final TransactionTemplate readOnlyTx;
  private final TransactionTemplate requiresNewTx;

  public InvoiceBatchServiceImpl(
      ContractRepository contractRepository,
      ContractRenewalRepository contractRenewalRepository,
      InvoiceRepository invoiceRepository,
      InvoiceIssuanceService invoiceIssuanceService,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.contractRepository = contractRepository;
    this.contractRenewalRepository = contractRenewalRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceIssuanceService = invoiceIssuanceService;
    this.clock = clock;

    this.readOnlyTx = new TransactionTemplate(transactionManager);
    this.readOnlyTx.setReadOnly(true);

    this.requiresNewTx = new TransactionTemplate(transactionManager);
    this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    // Keep a single snapshot per sub-transaction so concurrent inserts trigger 23505 instead of
    // silently returning an existing invoice inside issueMonthlyInvoiceIfAbsent as newly ISSUED.
    this.requiresNewTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  @Override
  public InvoiceBatchIssuanceResponse issueMonthlyBatch(LocalDate requestedBillingPeriod) {
    LocalDate today = LocalDate.now(clock.withZone(TimeConfig.DORM_ZONE));
    LocalDate targetPeriod = resolveTargetBillingPeriod(requestedBillingPeriod, today);

    List<UUID> candidateContractIds =
        readOnlyTx.execute(
            status ->
                contractRepository
                    .findActiveContractsCoveringBillingPeriod(ContractStatus.ACTIVE, targetPeriod)
                    .stream()
                    .map(Contract::getId)
                    .toList());

    if (candidateContractIds == null || candidateContractIds.isEmpty()) {
      log.info(
          "Monthly invoice batch completed for billingPeriod={}: eligible=0, issued=0, skippedExisting=0",
          targetPeriod);
      return InvoiceBatchIssuanceResponse.builder()
          .billingPeriod(targetPeriod)
          .eligibleContractCount(0)
          .issuedCount(0)
          .skippedExistingCount(0)
          .issuedInvoices(List.of())
          .build();
    }

    int eligibleCount = 0;
    int skippedExistingCount = 0;
    List<InvoiceSummaryResponse> issuedInvoices = new ArrayList<>();

    for (UUID contractId : candidateContractIds) {
      try {
        // Each contract runs in its own REQUIRES_NEW sub-transaction so a concurrent duplicate
        // key race rolls back only that contract and never aborts the PostgreSQL session (25P02).
        ContractBatchOutcome outcome =
            requiresNewTx.execute(
                status -> processSingleContract(contractId, targetPeriod, today));
        if (outcome == null || outcome.status() == OutcomeStatus.NOT_ELIGIBLE) {
          continue;
        }
        eligibleCount++;
        if (outcome.status() == OutcomeStatus.SKIPPED_EXISTING) {
          skippedExistingCount++;
        } else if (outcome.status() == OutcomeStatus.ISSUED && outcome.invoice() != null) {
          issuedInvoices.add(outcome.invoice());
        }
      } catch (DataIntegrityViolationException ex) {
        if (DataIntegrityViolationClassifier.matchesUniqueConstraint(
            ex, "uq_invoices_contract_period", "invoices_invoice_code_key")) {
          eligibleCount++;
          skippedExistingCount++;
          log.debug(
              "Skipped concurrently issued invoice for contractId={} billingPeriod={}",
              contractId,
              targetPeriod);
        } else {
          throw ex;
        }
      }
    }

    log.info(
        "Monthly invoice batch completed for billingPeriod={}: eligible={}, issued={}, skippedExisting={}",
        targetPeriod,
        eligibleCount,
        issuedInvoices.size(),
        skippedExistingCount);

    return InvoiceBatchIssuanceResponse.builder()
        .billingPeriod(targetPeriod)
        .eligibleContractCount(eligibleCount)
        .issuedCount(issuedInvoices.size())
        .skippedExistingCount(skippedExistingCount)
        .issuedInvoices(issuedInvoices)
        .build();
  }

  private LocalDate resolveTargetBillingPeriod(LocalDate requestedBillingPeriod, LocalDate today) {
    LocalDate currentMonthFirstDay = YearMonth.from(today).atDay(1);
    LocalDate nextMonthFirstDay = YearMonth.from(today).plusMonths(1).atDay(1);

    if (requestedBillingPeriod == null) {
      return today.getDayOfMonth() >= MONTHLY_ISSUANCE_DAY
          ? nextMonthFirstDay
          : currentMonthFirstDay;
    }

    if (requestedBillingPeriod.getDayOfMonth() != 1) {
      throw new BaseAppException(InvoiceErrorCode.INVALID_BILLING_PERIOD);
    }
    if (requestedBillingPeriod.isAfter(nextMonthFirstDay)) {
      throw new BaseAppException(InvoiceErrorCode.FUTURE_BILLING_PERIOD_NOT_ALLOWED);
    }
    if (requestedBillingPeriod.equals(nextMonthFirstDay)
        && today.getDayOfMonth() < MONTHLY_ISSUANCE_DAY) {
      throw new BaseAppException(InvoiceErrorCode.ISSUANCE_WINDOW_NOT_OPEN);
    }
    return requestedBillingPeriod;
  }

  private ContractBatchOutcome processSingleContract(
      UUID contractId, LocalDate targetPeriod, LocalDate today) {
    Contract contract = contractRepository.findById(contractId).orElse(null);
    if (contract == null
        || contract.getStatus() != ContractStatus.ACTIVE
        || contract.getStartOn() == null
        || contract.getStartOn().isAfter(targetPeriod)) {
      return ContractBatchOutcome.notEligible();
    }

    BigDecimal lockedRent = resolveLockedMonthlyRent(contract, targetPeriod);
    if (lockedRent == null) {
      return ContractBatchOutcome.notEligible();
    }

    if (invoiceRepository.findByContractIdAndBillingPeriod(contractId, targetPeriod).isPresent()) {
      return ContractBatchOutcome.skippedExisting();
    }

    Invoice created =
        invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(contract, targetPeriod, lockedRent);
    return ContractBatchOutcome.issued(toSummaryResponse(created, contract, today));
  }

  private BigDecimal resolveLockedMonthlyRent(Contract contract, LocalDate targetPeriod) {
    List<ContractRenewal> covering =
        contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
            contract.getId(), targetPeriod);
    if (!covering.isEmpty()) {
      return covering.getFirst().getMonthlyRent();
    }
    if (contract.getOriginalEndOn() != null && !targetPeriod.isAfter(contract.getOriginalEndOn())) {
      return contract.getOriginalMonthlyRent();
    }
    return null;
  }

  private static InvoiceSummaryResponse toSummaryResponse(
      Invoice invoice, Contract fallbackContract, LocalDate today) {
    Contract contract = invoice.getContract() != null ? invoice.getContract() : fallbackContract;
    Tenant tenant = contract != null ? contract.getTenant() : null;
    User user = tenant != null ? tenant.getUser() : null;

    boolean overdue =
        invoice.getStatus() == InvoiceStatus.DEBT
            && invoice.getDueOn() != null
            && invoice.getDueOn().isBefore(today);
    long overdueDays = overdue ? ChronoUnit.DAYS.between(invoice.getDueOn(), today) : 0L;
    boolean proofAvailable =
        invoice.getProofFileKey() != null && !invoice.getProofFileKey().isBlank();

    return InvoiceSummaryResponse.builder()
        .id(invoice.getId())
        .invoiceCode(invoice.getInvoiceCode())
        .contractId(contract != null ? contract.getId() : null)
        .contractCode(contract != null ? contract.getContractCode() : null)
        .tenantId(tenant != null ? tenant.getId() : null)
        .tenantCode(tenant != null ? tenant.getTenantCode() : null)
        .tenantName(tenant != null ? tenant.getFullName() : null)
        .tenantPhone(tenant != null ? tenant.getPhone() : null)
        .tenantIdCardNumber(tenant != null ? tenant.getIdCardNumber() : null)
        .tenantEmail(user != null ? user.getEmail() : null)
        .roomNumber(contract != null ? contract.getRoomCodeSnapshot() : null)
        .bedCode(contract != null ? contract.getBedCodeSnapshot() : null)
        .billingPeriod(invoice.getBillingPeriod())
        .amount(invoice.getAmount())
        .dueOn(invoice.getDueOn())
        .status(invoice.getStatus())
        .overdue(overdue)
        .overdueDays(overdueDays)
        .proofAvailable(proofAvailable)
        .proofSubmittedAt(invoice.getProofSubmittedAt())
        .payment(toPaymentTransactionResponse(invoice.getPaymentTransaction()))
        .issuedAt(invoice.getIssuedAt())
        .build();
  }

  private static PaymentTransactionResponse toPaymentTransactionResponse(PaymentTransaction tx) {
    if (tx == null) {
      return null;
    }
    StaffProfile staff = tx.getConfirmedByStaff();
    return PaymentTransactionResponse.builder()
        .id(tx.getId())
        .receiptCode(tx.getReceiptCode())
        .amount(tx.getAmount())
        .paymentMethod(tx.getPaymentMethod())
        .confirmedByStaffId(staff != null ? staff.getId() : null)
        .confirmedByStaffName(staff != null ? staff.getFullName() : null)
        .confirmedAt(tx.getConfirmedAt())
        .build();
  }

  private enum OutcomeStatus {
    NOT_ELIGIBLE,
    SKIPPED_EXISTING,
    ISSUED
  }

  private record ContractBatchOutcome(OutcomeStatus status, InvoiceSummaryResponse invoice) {

    private static ContractBatchOutcome notEligible() {
      return new ContractBatchOutcome(OutcomeStatus.NOT_ELIGIBLE, null);
    }

    private static ContractBatchOutcome skippedExisting() {
      return new ContractBatchOutcome(OutcomeStatus.SKIPPED_EXISTING, null);
    }

    private static ContractBatchOutcome issued(InvoiceSummaryResponse invoice) {
      return new ContractBatchOutcome(OutcomeStatus.ISSUED, invoice);
    }
  }
}
