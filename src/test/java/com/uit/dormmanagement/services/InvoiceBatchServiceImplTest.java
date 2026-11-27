package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceBatchIssuanceResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.services.impl.InvoiceBatchServiceImpl;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class InvoiceBatchServiceImplTest {

  @Mock private ContractRepository contractRepository;
  @Mock private ContractRenewalRepository contractRenewalRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceIssuanceService invoiceIssuanceService;

  private RecordingTransactionManager transactionManager;

  private InvoiceBatchServiceImpl createService(Clock clock) {
    transactionManager = new RecordingTransactionManager();
    return new InvoiceBatchServiceImpl(
        contractRepository,
        contractRenewalRepository,
        invoiceRepository,
        invoiceIssuanceService,
        clock,
        transactionManager);
  }

  private InvoiceBatchServiceImpl createServiceAtDormLocalTime(
      int year, int month, int day, int hour, int minute) {
    Instant instant =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TimeConfig.DORM_ZONE).toInstant();
    return createService(Clock.fixed(instant, TimeConfig.DORM_ZONE));
  }

  private Contract buildContract(
      String contractCode,
      LocalDate startOn,
      LocalDate originalEndOn,
      BigDecimal originalMonthlyRent,
      BigDecimal currentRoomBasePrice) {
    Room room =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.101")
            .basePrice(currentRoomBasePrice)
            .build();
    Bed bed = Bed.builder().id(UUID.randomUUID()).room(room).bedCode("B1").build();
    User user = User.builder().id(UUID.randomUUID()).email(contractCode + "@uit.edu.vn").build();
    Tenant tenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .user(user)
            .tenantCode("NT-" + contractCode)
            .fullName("Tenant " + contractCode)
            .phone("0901234567")
            .idCardNumber("079203000001")
            .build();
    return Contract.builder()
        .id(UUID.randomUUID())
        .contractCode(contractCode)
        .tenant(tenant)
        .bed(bed)
        .roomCodeSnapshot("P.101")
        .bedCodeSnapshot("B1")
        .startOn(startOn)
        .originalEndOn(originalEndOn)
        .originalMonthlyRent(originalMonthlyRent)
        .depositAmount(originalMonthlyRent)
        .status(ContractStatus.ACTIVE)
        .build();
  }

  private Invoice buildIssuedInvoice(
      Contract contract, LocalDate billingPeriod, BigDecimal amount) {
    return Invoice.builder()
        .id(UUID.randomUUID())
        .invoiceCode(
            "INV-"
                + billingPeriod.getYear()
                + String.format("%02d", billingPeriod.getMonthValue())
                + "-"
                + contract.getContractCode())
        .contract(contract)
        .billingPeriod(billingPeriod)
        .amount(amount)
        .dueOn(billingPeriod.withDayOfMonth(5))
        .status(InvoiceStatus.DEBT)
        .issuedAt(Instant.parse("2026-10-25T00:00:05Z"))
        .build();
  }

  @Test
  @DisplayName(
      "InvoiceBatchServiceImpl and issueMonthlyBatch must NOT declare outer @Transactional")
  void issueMonthlyBatch_mustNotHaveOuterTransactional() throws Exception {
    assertThat(InvoiceBatchServiceImpl.class.getAnnotation(Transactional.class)).isNull();
    Method method =
        InvoiceBatchServiceImpl.class.getMethod("issueMonthlyBatch", LocalDate.class);
    assertThat(method.getAnnotation(Transactional.class)).isNull();
  }

  @Test
  @DisplayName(
      "Default billingPeriod when null resolves current month on day 24 and next month on day 25 in Asia/Ho_Chi_Minh")
  void issueMonthlyBatch_nullBillingPeriod_resolvesByDormDay25Boundary() {
    // 2026-10-24T16:59:59Z == 2026-10-24T23:59:59+07:00 -> day 24 -> 2026-10-01
    InvoiceBatchServiceImpl day24Service =
        createService(Clock.fixed(Instant.parse("2026-10-24T16:59:59Z"), ZoneOffset.UTC));
    when(contractRepository.findActiveContractsCoveringBillingPeriod(
            ContractStatus.ACTIVE, LocalDate.of(2026, 10, 1)))
        .thenReturn(List.of());

    InvoiceBatchIssuanceResponse day24Res = day24Service.issueMonthlyBatch(null);
    assertThat(day24Res.billingPeriod()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(day24Res.eligibleContractCount()).isZero();
    assertThat(day24Res.issuedCount()).isZero();
    assertThat(day24Res.skippedExistingCount()).isZero();
    assertThat(day24Res.issuedInvoices()).isEmpty();
    verify(contractRepository)
        .findActiveContractsCoveringBillingPeriod(ContractStatus.ACTIVE, LocalDate.of(2026, 10, 1));

    // 2026-10-24T17:00:00Z == 2026-10-25T00:00:00+07:00 -> day 25 -> 2026-11-01
    InvoiceBatchServiceImpl day25Service =
        createService(Clock.fixed(Instant.parse("2026-10-24T17:00:00Z"), ZoneOffset.UTC));
    when(contractRepository.findActiveContractsCoveringBillingPeriod(
            ContractStatus.ACTIVE, LocalDate.of(2026, 11, 1)))
        .thenReturn(List.of());

    InvoiceBatchIssuanceResponse day25Res = day25Service.issueMonthlyBatch(null);
    assertThat(day25Res.billingPeriod()).isEqualTo(LocalDate.of(2026, 11, 1));
    verify(contractRepository)
        .findActiveContractsCoveringBillingPeriod(ContractStatus.ACTIVE, LocalDate.of(2026, 11, 1));

    // Year rollover: 2026-12-25 -> 2027-01-01
    InvoiceBatchServiceImpl dec25Service = createServiceAtDormLocalTime(2026, 12, 25, 0, 5);
    when(contractRepository.findActiveContractsCoveringBillingPeriod(
            ContractStatus.ACTIVE, LocalDate.of(2027, 1, 1)))
        .thenReturn(List.of());

    InvoiceBatchIssuanceResponse dec25Res = dec25Service.issueMonthlyBatch(null);
    assertThat(dec25Res.billingPeriod()).isEqualTo(LocalDate.of(2027, 1, 1));
  }

  @Test
  @DisplayName(
      "Rejects non-day-01, future beyond next month, and next month before day 25 without touching DB")
  void issueMonthlyBatch_rejectsInvalidWindowsWithoutDatabaseCalls() {
    InvoiceBatchServiceImpl day24Service =
        createService(Clock.fixed(Instant.parse("2026-10-24T16:59:59Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> day24Service.issueMonthlyBatch(LocalDate.of(2026, 10, 15)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVALID_BILLING_PERIOD);

    assertThatThrownBy(() -> day24Service.issueMonthlyBatch(LocalDate.of(2026, 12, 1)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", InvoiceErrorCode.FUTURE_BILLING_PERIOD_NOT_ALLOWED);

    assertThatThrownBy(() -> day24Service.issueMonthlyBatch(LocalDate.of(2026, 11, 1)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.ISSUANCE_WINDOW_NOT_OPEN);

    InvoiceBatchServiceImpl day25Service = createServiceAtDormLocalTime(2026, 10, 25, 0, 0);
    assertThatThrownBy(() -> day25Service.issueMonthlyBatch(LocalDate.of(2026, 12, 1)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", InvoiceErrorCode.FUTURE_BILLING_PERIOD_NOT_ALLOWED);

    verifyNoInteractions(
        contractRepository, contractRenewalRepository, invoiceRepository, invoiceIssuanceService);
    assertThat(transactionManager.startedDefinitions).isEmpty();
  }

  @Test
  @DisplayName(
      "Resolves locked rent from original term or exact covering renewal (ignoring room.basePrice) and computes overdue dynamically")
  void issueMonthlyBatch_resolvesLockedRentAndSummaryFields() {
    // Today = 2026-10-10; requesting past period 2026-09-01 (dueOn 2026-09-05 -> 35 overdueDays)
    InvoiceBatchServiceImpl service = createServiceAtDormLocalTime(2026, 10, 10, 9, 0);
    LocalDate targetPeriod = LocalDate.of(2026, 9, 1);

    Contract originalContract =
        buildContract(
            "HD-2026-0001",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1500000"),
            new BigDecimal("2300000"));
    Contract renewedContract =
        buildContract(
            "HD-2026-0002",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30),
            new BigDecimal("1400000"),
            new BigDecimal("2500000"));
    Contract concurrentLiquidated =
        buildContract(
            "HD-2026-0003",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2027, 1, 31),
            new BigDecimal("1500000"),
            new BigDecimal("2500000"));
    Contract expiredNoCoveringRenewal =
        buildContract(
            "HD-2026-0004",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 8, 31),
            new BigDecimal("1500000"),
            new BigDecimal("2500000"));

    ContractRenewal coveringRenewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(renewedContract)
            .sequenceNo(1)
            .effectiveFrom(LocalDate.of(2026, 7, 1))
            .newEndOn(LocalDate.of(2026, 12, 31))
            .monthlyRent(new BigDecimal("1800000"))
            .build();

    when(contractRepository.findActiveContractsCoveringBillingPeriod(
            ContractStatus.ACTIVE, targetPeriod))
        .thenReturn(
            List.of(
                originalContract,
                renewedContract,
                concurrentLiquidated,
                expiredNoCoveringRenewal));

    when(contractRepository.findById(originalContract.getId()))
        .thenReturn(Optional.of(originalContract));
    when(contractRepository.findById(renewedContract.getId()))
        .thenReturn(Optional.of(renewedContract));
    concurrentLiquidated.setStatus(ContractStatus.LIQUIDATED);
    when(contractRepository.findById(concurrentLiquidated.getId()))
        .thenReturn(Optional.of(concurrentLiquidated));
    when(contractRepository.findById(expiredNoCoveringRenewal.getId()))
        .thenReturn(Optional.of(expiredNoCoveringRenewal));

    when(contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
            originalContract.getId(), targetPeriod))
        .thenReturn(List.of());
    when(contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
            renewedContract.getId(), targetPeriod))
        .thenReturn(List.of(coveringRenewal));
    when(contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
            expiredNoCoveringRenewal.getId(), targetPeriod))
        .thenReturn(List.of());

    when(invoiceRepository.findByContractIdAndBillingPeriod(any(), eq(targetPeriod)))
        .thenReturn(Optional.empty());

    Invoice origInvoice =
        buildIssuedInvoice(originalContract, targetPeriod, new BigDecimal("1500000"));
    Invoice renewalInvoice =
        buildIssuedInvoice(renewedContract, targetPeriod, new BigDecimal("1800000"));

    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            originalContract, targetPeriod, new BigDecimal("1500000")))
        .thenReturn(origInvoice);
    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            renewedContract, targetPeriod, new BigDecimal("1800000")))
        .thenReturn(renewalInvoice);

    InvoiceBatchIssuanceResponse response = service.issueMonthlyBatch(targetPeriod);

    assertThat(response.billingPeriod()).isEqualTo(targetPeriod);
    assertThat(response.eligibleContractCount()).isEqualTo(2);
    assertThat(response.issuedCount()).isEqualTo(2);
    assertThat(response.skippedExistingCount()).isZero();
    assertThat(response.issuedInvoices()).hasSize(2);

    InvoiceSummaryResponse firstSummary = response.issuedInvoices().get(0);
    assertThat(firstSummary.id()).isEqualTo(origInvoice.getId());
    assertThat(firstSummary.invoiceCode()).isEqualTo("INV-202609-HD-2026-0001");
    assertThat(firstSummary.contractId()).isEqualTo(originalContract.getId());
    assertThat(firstSummary.contractCode()).isEqualTo("HD-2026-0001");
    assertThat(firstSummary.tenantId()).isEqualTo(originalContract.getTenant().getId());
    assertThat(firstSummary.tenantCode()).isEqualTo("NT-HD-2026-0001");
    assertThat(firstSummary.tenantName()).isEqualTo("Tenant HD-2026-0001");
    assertThat(firstSummary.tenantPhone()).isEqualTo("0901234567");
    assertThat(firstSummary.tenantIdCardNumber()).isEqualTo("079203000001");
    assertThat(firstSummary.tenantEmail()).isEqualTo("HD-2026-0001@uit.edu.vn");
    assertThat(firstSummary.roomNumber()).isEqualTo("P.101");
    assertThat(firstSummary.bedCode()).isEqualTo("B1");
    assertThat(firstSummary.billingPeriod()).isEqualTo(targetPeriod);
    assertThat(firstSummary.amount()).isEqualByComparingTo("1500000");
    assertThat(firstSummary.dueOn()).isEqualTo(LocalDate.of(2026, 9, 5));
    assertThat(firstSummary.status()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(firstSummary.overdue()).isTrue();
    assertThat(firstSummary.overdueDays()).isEqualTo(35L);
    assertThat(firstSummary.proofAvailable()).isFalse();
    assertThat(firstSummary.proofSubmittedAt()).isNull();
    assertThat(firstSummary.payment()).isNull();
    assertThat(firstSummary.issuedAt()).isEqualTo(origInvoice.getIssuedAt());

    assertThat(response.issuedInvoices().get(1).amount()).isEqualByComparingTo("1800000");
    assertThatThrownBy(() -> response.issuedInvoices().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    verify(invoiceIssuanceService, never())
        .issueMonthlyInvoiceIfAbsent(eq(concurrentLiquidated), any(), any());
    verify(invoiceIssuanceService, never())
        .issueMonthlyInvoiceIfAbsent(eq(expiredNoCoveringRenewal), any(), any());
  }

  @Test
  @DisplayName(
      "Due date boundary: dueOn == today is NOT overdue, while dueOn < today computes exact overdueDays")
  void issueMonthlyBatch_dueDateBoundary_notOverdueOnDueDate() {
    InvoiceBatchServiceImpl service = createServiceAtDormLocalTime(2026, 10, 5, 12, 0);
    LocalDate period = LocalDate.of(2026, 10, 1);
    Contract contract =
        buildContract(
            "HD-2026-0010",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2027, 3, 31),
            new BigDecimal("1500000"),
            new BigDecimal("1500000"));
    Invoice invoice = buildIssuedInvoice(contract, period, new BigDecimal("1500000"));

    when(contractRepository.findActiveContractsCoveringBillingPeriod(ContractStatus.ACTIVE, period))
        .thenReturn(List.of(contract));
    when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(contract.getId(), period))
        .thenReturn(List.of());
    when(invoiceRepository.findByContractIdAndBillingPeriod(contract.getId(), period))
        .thenReturn(Optional.empty());
    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            contract, period, new BigDecimal("1500000")))
        .thenReturn(invoice);

    InvoiceBatchIssuanceResponse response = service.issueMonthlyBatch(period);
    assertThat(response.issuedInvoices()).hasSize(1);
    assertThat(response.issuedInvoices().getFirst().overdue()).isFalse();
    assertThat(response.issuedInvoices().getFirst().overdueDays()).isZero();
  }

  @Test
  @DisplayName(
      "Concurrent uq_invoices_contract_period / invoices_invoice_code_key race rolls back ONLY failing REQUIRES_NEW sub-tx and lets remaining contracts commit")
  void issueMonthlyBatch_uniqueViolationOnOneContract_isolatesRollbackAndContinuesBatch() {
    InvoiceBatchServiceImpl service = createServiceAtDormLocalTime(2026, 10, 25, 0, 5);
    LocalDate targetPeriod = LocalDate.of(2026, 11, 1);

    Contract contract1 =
        buildContract(
            "HD-2026-0101",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1500000"),
            new BigDecimal("1500000"));
    Contract contract2 =
        buildContract(
            "HD-2026-0102",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1600000"),
            new BigDecimal("1600000"));
    Contract contract3 =
        buildContract(
            "HD-2026-0103",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1700000"),
            new BigDecimal("1700000"));

    when(contractRepository.findActiveContractsCoveringBillingPeriod(
            ContractStatus.ACTIVE, targetPeriod))
        .thenReturn(List.of(contract1, contract2, contract3));
    when(contractRepository.findById(contract1.getId())).thenReturn(Optional.of(contract1));
    when(contractRepository.findById(contract2.getId())).thenReturn(Optional.of(contract2));
    when(contractRepository.findById(contract3.getId())).thenReturn(Optional.of(contract3));
    lenient()
        .when(contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(any(), any()))
        .thenReturn(List.of());

    // Contract 1 passes initial pre-check then hits unique constraint race on saveAndFlush
    when(invoiceRepository.findByContractIdAndBillingPeriod(contract1.getId(), targetPeriod))
        .thenReturn(Optional.empty());
    SQLException pgUniqueEx =
        new SQLException(
            "duplicate key value violates unique constraint \"uq_invoices_contract_period\"",
            "23505");
    DataIntegrityViolationException uqRace =
        new DataIntegrityViolationException(
            "could not execute statement",
            new ConstraintViolationException(
                "could not execute statement", pgUniqueEx, "uq_invoices_contract_period"));
    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            contract1, targetPeriod, new BigDecimal("1500000")))
        .thenThrow(uqRace);

    // Contract 2 already exists in DB (clean pre-check skip)
    when(invoiceRepository.findByContractIdAndBillingPeriod(contract2.getId(), targetPeriod))
        .thenReturn(Optional.of(buildIssuedInvoice(contract2, targetPeriod, new BigDecimal("1600000"))));

    // Contract 3 hits invoices_invoice_code_key race? Let's test normal issuance for contract3 AND
    // invoices_invoice_code_key in another contract or test contract3 issuing cleanly
    when(invoiceRepository.findByContractIdAndBillingPeriod(contract3.getId(), targetPeriod))
        .thenReturn(Optional.empty());
    Invoice contract3Invoice =
        buildIssuedInvoice(contract3, targetPeriod, new BigDecimal("1700000"));
    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            contract3, targetPeriod, new BigDecimal("1700000")))
        .thenReturn(contract3Invoice);

    InvoiceBatchIssuanceResponse response = service.issueMonthlyBatch(targetPeriod);

    assertThat(response.eligibleContractCount()).isEqualTo(3);
    assertThat(response.issuedCount()).isEqualTo(1);
    assertThat(response.skippedExistingCount()).isEqualTo(2);
    assertThat(response.issuedInvoices()).hasSize(1);
    assertThat(response.issuedInvoices().getFirst().contractId()).isEqualTo(contract3.getId());

    // Verify 1 readOnlyTx + 3 REQUIRES_NEW sub-transactions; 1 rolled back, 3 committed
    assertThat(transactionManager.startedDefinitions).hasSize(4);
    assertThat(transactionManager.startedDefinitions.get(0).isReadOnly()).isTrue();
    for (int i = 1; i <= 3; i++) {
      assertThat(transactionManager.startedDefinitions.get(i).getPropagationBehavior())
          .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      assertThat(transactionManager.startedDefinitions.get(i).getIsolationLevel())
          .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
      assertThat(transactionManager.startedDefinitions.get(i).isReadOnly()).isFalse();
    }
    assertThat(transactionManager.rollbackCount).isEqualTo(1);
    assertThat(transactionManager.commitCount).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "invoices_invoice_code_key unique constraint is skipped idempotently while non-matching DataIntegrityViolationException is rethrown")
  void issueMonthlyBatch_invoiceCodeConstraintSkipped_andUnrelatedIntegrityErrorRethrown() {
    InvoiceBatchServiceImpl service = createServiceAtDormLocalTime(2026, 10, 25, 0, 5);
    LocalDate targetPeriod = LocalDate.of(2026, 11, 1);

    Contract codeRaceContract =
        buildContract(
            "HD-2026-0201",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1500000"),
            new BigDecimal("1500000"));
    Contract checkErrorContract =
        buildContract(
            "HD-2026-0202",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1500000"),
            new BigDecimal("1500000"));

    when(contractRepository.findActiveContractsCoveringBillingPeriod(
            ContractStatus.ACTIVE, targetPeriod))
        .thenReturn(List.of(codeRaceContract, checkErrorContract));
    when(contractRepository.findById(codeRaceContract.getId()))
        .thenReturn(Optional.of(codeRaceContract));
    when(contractRepository.findById(checkErrorContract.getId()))
        .thenReturn(Optional.of(checkErrorContract));
    when(contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(any(), any()))
        .thenReturn(List.of());
    when(invoiceRepository.findByContractIdAndBillingPeriod(any(), eq(targetPeriod)))
        .thenReturn(Optional.empty());

    SQLException codeUniqueSqlEx =
        new SQLException(
            "duplicate key value violates unique constraint \"invoices_invoice_code_key\"",
            "23505");
    DataIntegrityViolationException codeRace =
        new DataIntegrityViolationException(
            "could not execute statement",
            new ConstraintViolationException(
                "could not execute statement", codeUniqueSqlEx, "invoices_invoice_code_key"));

    SQLException checkSqlEx = new SQLException("check violation", "23514");
    DataIntegrityViolationException checkViolation =
        new DataIntegrityViolationException(
            "could not execute statement",
            new ConstraintViolationException(
                "could not execute statement", checkSqlEx, "ck_invoices_amount"));

    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            codeRaceContract, targetPeriod, new BigDecimal("1500000")))
        .thenThrow(codeRace);
    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            checkErrorContract, targetPeriod, new BigDecimal("1500000")))
        .thenThrow(checkViolation);

    assertThatThrownBy(() -> service.issueMonthlyBatch(targetPeriod)).isSameAs(checkViolation);
    assertThat(transactionManager.rollbackCount).isEqualTo(2);
  }

  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    private final List<TransactionDefinition> startedDefinitions = new ArrayList<>();
    private int commitCount;
    private int rollbackCount;

    @Override
    protected Object doGetTransaction() throws TransactionException {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition)
        throws TransactionException {
      startedDefinitions.add(definition);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
      commitCount++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
      rollbackCount++;
    }
  }
}
