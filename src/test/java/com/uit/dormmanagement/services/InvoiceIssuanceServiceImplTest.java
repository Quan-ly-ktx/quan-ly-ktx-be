package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.services.impl.InvoiceIssuanceServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceIssuanceServiceImplTest {

  @Mock private InvoiceRepository invoiceRepository;

  private InvoiceIssuanceServiceImpl createServiceAtDormLocalTime(
      int year, int month, int day, int hour, int minute) {
    Instant instant =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TimeConfig.DORM_ZONE).toInstant();
    return new InvoiceIssuanceServiceImpl(
        invoiceRepository, Clock.fixed(instant, TimeConfig.DORM_ZONE));
  }

  private InvoiceIssuanceServiceImpl createServiceWithClock(Clock clock) {
    return new InvoiceIssuanceServiceImpl(invoiceRepository, clock);
  }

  private Contract activeContract() {
    return Contract.builder()
        .id(UUID.randomUUID())
        .contractCode("HD-2026-0001")
        .startOn(LocalDate.of(2026, 9, 1))
        .originalEndOn(LocalDate.of(2026, 11, 30))
        .originalMonthlyRent(new BigDecimal("1500000"))
        .depositAmount(new BigDecimal("1500000"))
        .status(ContractStatus.ACTIVE)
        .build();
  }

  @Test
  @DisplayName("TimeConfig anchors dormClock to Asia/Ho_Chi_Minh")
  void timeConfig_dormClockUsesAsiaHoChiMinh() {
    TimeConfig timeConfig = new TimeConfig();
    assertThat(TimeConfig.DORM_ZONE).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    assertThat(timeConfig.dormClock().getZone()).isEqualTo(TimeConfig.DORM_ZONE);
  }

  @Test
  @DisplayName(
      "hasUnsettledInvoices returns true for DEBT and PENDING_CONFIRMATION, false for PAID/none")
  void hasUnsettledInvoices_checksUnsettledStatusesAndRejectsNull() {
    InvoiceIssuanceServiceImpl service = createServiceAtDormLocalTime(2026, 11, 1, 9, 0);
    UUID tenantWithDebt = UUID.randomUUID();
    UUID tenantWithPending = UUID.randomUUID();
    UUID tenantWithPaidOnly = UUID.randomUUID();
    UUID tenantWithNone = UUID.randomUUID();

    stubTenantInvoiceStatuses(tenantWithDebt, Set.of(InvoiceStatus.DEBT));
    stubTenantInvoiceStatuses(tenantWithPending, Set.of(InvoiceStatus.PENDING_CONFIRMATION));
    stubTenantInvoiceStatuses(tenantWithPaidOnly, Set.of(InvoiceStatus.PAID));
    stubTenantInvoiceStatuses(tenantWithNone, Set.of());

    assertThat(service.hasUnsettledInvoices(tenantWithDebt)).isTrue();
    assertThat(service.hasUnsettledInvoices(tenantWithPending)).isTrue();
    assertThat(service.hasUnsettledInvoices(tenantWithPaidOnly)).isFalse();
    assertThat(service.hasUnsettledInvoices(tenantWithNone)).isFalse();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<InvoiceStatus>> statusesCaptor =
        ArgumentCaptor.forClass(Collection.class);
    verify(invoiceRepository)
        .existsByContractTenantIdAndStatusIn(eq(tenantWithDebt), statusesCaptor.capture());
    assertThat(statusesCaptor.getValue())
        .containsExactlyInAnyOrder(InvoiceStatus.DEBT, InvoiceStatus.PENDING_CONFIRMATION);

    assertThatThrownBy(() -> service.hasUnsettledInvoices(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void stubTenantInvoiceStatuses(UUID tenantId, Set<InvoiceStatus> existingStatuses) {
    when(invoiceRepository.existsByContractTenantIdAndStatusIn(eq(tenantId), anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<InvoiceStatus> queried = inv.getArgument(1);
              return existingStatuses.stream().anyMatch(queried::contains);
            });
  }

  @Test
  @DisplayName(
      "issueMonthlyInvoiceIfAbsent creates DEBT invoice with day-05 dueOn and normalized VND")
  void issueMonthlyInvoiceIfAbsent_createsNormalizedDebtInvoice() {
    InvoiceIssuanceServiceImpl service = createServiceAtDormLocalTime(2026, 11, 1, 9, 0);
    Contract contract = activeContract();
    contract.setContractCode("  HD-2026-0001  ");
    LocalDate billingPeriod = LocalDate.of(2026, 11, 1);

    when(invoiceRepository.findByContractIdAndBillingPeriod(contract.getId(), billingPeriod))
        .thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class)))
        .thenAnswer(
            inv -> {
              Invoice toSave = inv.getArgument(0);
              toSave.setId(UUID.randomUUID());
              return toSave;
            });

    Invoice created =
        service.issueMonthlyInvoiceIfAbsent(contract, billingPeriod, new BigDecimal("1500000.00"));

    assertThat(created.getInvoiceCode()).isEqualTo("INV-202611-HD-2026-0001");
    assertThat(created.getContract()).isSameAs(contract);
    assertThat(created.getBillingPeriod()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(created.getDueOn()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(created.getAmount()).isEqualByComparingTo("1500000");
    assertThat(created.getAmount().scale()).isZero();
    assertThat(created.getStatus()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(created.getProofFileKey()).isNull();
    assertThat(created.getProofSubmittedAt()).isNull();

    verify(invoiceRepository).saveAndFlush(any(Invoice.class));
  }

  @Test
  @DisplayName(
      "issueMonthlyInvoiceIfAbsent is idempotent when invoice already exists for contract/period")
  void issueMonthlyInvoiceIfAbsent_whenAlreadyExists_returnsExistingUnchanged() {
    InvoiceIssuanceServiceImpl service = createServiceAtDormLocalTime(2026, 11, 1, 9, 0);
    Contract contract = activeContract();
    LocalDate billingPeriod = LocalDate.of(2026, 11, 1);
    Invoice existing =
        Invoice.builder()
            .id(UUID.randomUUID())
            .invoiceCode("INV-202611-HD-2026-0001")
            .contract(contract)
            .billingPeriod(billingPeriod)
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 11, 5))
            .status(InvoiceStatus.PAID)
            .build();

    when(invoiceRepository.findByContractIdAndBillingPeriod(contract.getId(), billingPeriod))
        .thenReturn(Optional.of(existing));

    Invoice result =
        service.issueMonthlyInvoiceIfAbsent(contract, billingPeriod, new BigDecimal("1900000"));

    assertThat(result).isSameAs(existing);
    assertThat(result.getAmount()).isEqualByComparingTo("1500000");
    assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
    verify(invoiceRepository, never()).saveAndFlush(any());
    verify(invoiceRepository, never()).save(any());
  }

  @Test
  @DisplayName("issueMonthlyInvoiceIfAbsent rejects invalid contract, billingPeriod, and amount")
  void issueMonthlyInvoiceIfAbsent_rejectsInvalidInputs() {
    InvoiceIssuanceServiceImpl service = createServiceAtDormLocalTime(2026, 11, 1, 9, 0);
    Contract validContract = activeContract();
    LocalDate validPeriod = LocalDate.of(2026, 11, 1);
    BigDecimal validAmount = new BigDecimal("1500000");

    Contract nullIdContract = activeContract();
    nullIdContract.setId(null);

    Contract blankCodeContract = activeContract();
    blankCodeContract.setContractCode("   ");

    Contract nullCodeContract = activeContract();
    nullCodeContract.setContractCode(null);

    assertThatThrownBy(() -> service.issueMonthlyInvoiceIfAbsent(null, validPeriod, validAmount))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.issueMonthlyInvoiceIfAbsent(nullIdContract, validPeriod, validAmount))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.issueMonthlyInvoiceIfAbsent(blankCodeContract, validPeriod, validAmount))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.issueMonthlyInvoiceIfAbsent(nullCodeContract, validPeriod, validAmount))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.issueMonthlyInvoiceIfAbsent(validContract, null, validAmount))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.issueMonthlyInvoiceIfAbsent(
                    validContract, LocalDate.of(2026, 11, 2), validAmount))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.issueMonthlyInvoiceIfAbsent(validContract, validPeriod, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.issueMonthlyInvoiceIfAbsent(validContract, validPeriod, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.issueMonthlyInvoiceIfAbsent(
                    validContract, validPeriod, new BigDecimal("0.00")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.issueMonthlyInvoiceIfAbsent(
                    validContract, validPeriod, new BigDecimal("-1500000")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.issueMonthlyInvoiceIfAbsent(
                    validContract, validPeriod, new BigDecimal("1500000.50")))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(invoiceRepository);
  }

  @Test
  @DisplayName(
      "issueRenewalCatchUpInvoiceIfEligible returns empty on day 24 of previousEndOn month")
  void issueRenewalCatchUp_day24_returnsEmpty() {
    InvoiceIssuanceServiceImpl service = createServiceAtDormLocalTime(2026, 11, 24, 23, 59);
    Contract contract = activeContract();
    ContractRenewal renewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(contract)
            .sequenceNo(1)
            .signedOn(LocalDate.of(2026, 11, 24))
            .previousEndOn(LocalDate.of(2026, 11, 30))
            .effectiveFrom(LocalDate.of(2026, 12, 1))
            .newEndOn(LocalDate.of(2027, 5, 31))
            .monthlyRent(new BigDecimal("1750000"))
            .build();

    Optional<Invoice> result = service.issueRenewalCatchUpInvoiceIfEligible(contract, renewal);

    assertThat(result).isEmpty();
    verifyNoInteractions(invoiceRepository);
  }

  @Test
  @DisplayName(
      "issueRenewalCatchUpInvoiceIfEligible issues next-month invoice on day 25 and day 31")
  void issueRenewalCatchUp_day25AndDay31_issuesNextMonthInvoiceIdempotently() {
    Contract novContract = activeContract();
    ContractRenewal novRenewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(novContract)
            .sequenceNo(1)
            .signedOn(LocalDate.of(2026, 11, 25))
            .previousEndOn(LocalDate.of(2026, 11, 30))
            .effectiveFrom(LocalDate.of(2026, 12, 1))
            .newEndOn(LocalDate.of(2027, 5, 31))
            .monthlyRent(new BigDecimal("1750000"))
            .build();

    LocalDate decPeriod = LocalDate.of(2026, 12, 1);
    when(invoiceRepository.findByContractIdAndBillingPeriod(novContract.getId(), decPeriod))
        .thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class)))
        .thenAnswer(
            inv -> {
              Invoice saved = inv.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });

    InvoiceIssuanceServiceImpl day25Service = createServiceAtDormLocalTime(2026, 11, 25, 0, 5);
    Optional<Invoice> day25Result =
        day25Service.issueRenewalCatchUpInvoiceIfEligible(novContract, novRenewal);

    assertThat(day25Result).isPresent();
    Invoice decInvoice = day25Result.get();
    assertThat(decInvoice.getBillingPeriod()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(decInvoice.getDueOn()).isEqualTo(LocalDate.of(2026, 12, 5));
    assertThat(decInvoice.getAmount()).isEqualByComparingTo("1750000");
    assertThat(decInvoice.getStatus()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(decInvoice.getInvoiceCode()).isEqualTo("INV-202612-HD-2026-0001");

    // Second invocation when invoice already exists returns existing row without calling
    // saveAndFlush again
    when(invoiceRepository.findByContractIdAndBillingPeriod(novContract.getId(), decPeriod))
        .thenReturn(Optional.of(decInvoice));
    Optional<Invoice> idempotentResult =
        day25Service.issueRenewalCatchUpInvoiceIfEligible(novContract, novRenewal);
    assertThat(idempotentResult).containsSame(decInvoice);
    verify(invoiceRepository).saveAndFlush(any(Invoice.class));

    // Day 31 across year boundary (2026-12-31 -> 2027-01-01)
    Contract decContract = activeContract();
    decContract.setContractCode("HD-2026-0002");
    decContract.setOriginalEndOn(LocalDate.of(2026, 12, 31));
    ContractRenewal decRenewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(decContract)
            .sequenceNo(1)
            .signedOn(LocalDate.of(2026, 12, 31))
            .previousEndOn(LocalDate.of(2026, 12, 31))
            .effectiveFrom(LocalDate.of(2027, 1, 1))
            .newEndOn(LocalDate.of(2027, 6, 30))
            .monthlyRent(new BigDecimal("1850000.00"))
            .build();

    LocalDate janPeriod = LocalDate.of(2027, 1, 1);
    when(invoiceRepository.findByContractIdAndBillingPeriod(decContract.getId(), janPeriod))
        .thenReturn(Optional.empty());

    InvoiceIssuanceServiceImpl day31Service = createServiceAtDormLocalTime(2026, 12, 31, 23, 55);
    Optional<Invoice> day31Result =
        day31Service.issueRenewalCatchUpInvoiceIfEligible(decContract, decRenewal);

    assertThat(day31Result).isPresent();
    assertThat(day31Result.get().getBillingPeriod()).isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(day31Result.get().getDueOn()).isEqualTo(LocalDate.of(2027, 1, 5));
    assertThat(day31Result.get().getAmount()).isEqualByComparingTo("1850000");
    assertThat(day31Result.get().getAmount().scale()).isZero();
    assertThat(day31Result.get().getInvoiceCode()).isEqualTo("INV-202701-HD-2026-0002");
  }

  @Test
  @DisplayName(
      "issueRenewalCatchUpInvoiceIfEligible converts UTC instant to Asia/Ho_Chi_Minh for day-25 boundary")
  void issueRenewalCatchUp_timezoneBoundary_usesAsiaHoChiMinh() {
    Contract contract = activeContract();
    ContractRenewal renewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(contract)
            .sequenceNo(1)
            .signedOn(LocalDate.of(2026, 11, 25))
            .previousEndOn(LocalDate.of(2026, 11, 30))
            .effectiveFrom(LocalDate.of(2026, 12, 1))
            .newEndOn(LocalDate.of(2027, 5, 31))
            .monthlyRent(new BigDecimal("1650000"))
            .build();

    // 2026-11-24T16:59:59Z == 2026-11-24T23:59:59+07:00 -> day 24 in dorm zone -> empty
    Clock beforeMidnightDormClock =
        Clock.fixed(Instant.parse("2026-11-24T16:59:59Z"), ZoneOffset.UTC);
    assertThat(
            createServiceWithClock(beforeMidnightDormClock)
                .issueRenewalCatchUpInvoiceIfEligible(contract, renewal))
        .isEmpty();

    // 2026-11-24T17:30:00Z == 2026-11-25T00:30:00+07:00 -> day 25 in dorm zone -> issues Dec
    // invoice
    Clock afterMidnightDormClock =
        Clock.fixed(Instant.parse("2026-11-24T17:30:00Z"), ZoneOffset.UTC);
    LocalDate decPeriod = LocalDate.of(2026, 12, 1);
    when(invoiceRepository.findByContractIdAndBillingPeriod(contract.getId(), decPeriod))
        .thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<Invoice> result =
        createServiceWithClock(afterMidnightDormClock)
            .issueRenewalCatchUpInvoiceIfEligible(contract, renewal);

    assertThat(result).isPresent();
    assertThat(result.get().getBillingPeriod()).isEqualTo(decPeriod);
    assertThat(result.get().getDueOn()).isEqualTo(LocalDate.of(2026, 12, 5));
    assertThat(result.get().getAmount()).isEqualByComparingTo("1650000");
  }

  @Test
  @DisplayName(
      "issueRenewalCatchUpInvoiceIfEligible returns empty for earlier month, liquidated contract, or non-matching renewal")
  void issueRenewalCatchUp_ineligibleCases_returnEmpty() {
    Contract contract = activeContract();
    ContractRenewal novRenewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(contract)
            .sequenceNo(1)
            .signedOn(LocalDate.of(2026, 10, 26))
            .previousEndOn(LocalDate.of(2026, 11, 30))
            .effectiveFrom(LocalDate.of(2026, 12, 1))
            .newEndOn(LocalDate.of(2027, 5, 31))
            .monthlyRent(new BigDecimal("1750000"))
            .build();

    // Day 26 of October (earlier month than previousEndOn 2026-11-30) -> empty
    InvoiceIssuanceServiceImpl oct26Service = createServiceAtDormLocalTime(2026, 10, 26, 10, 0);
    assertThat(oct26Service.issueRenewalCatchUpInvoiceIfEligible(contract, novRenewal)).isEmpty();

    // Day 26 of November on LIQUIDATED contract -> empty
    InvoiceIssuanceServiceImpl nov26Service = createServiceAtDormLocalTime(2026, 11, 26, 10, 0);
    contract.setStatus(ContractStatus.LIQUIDATED);
    assertThat(nov26Service.issueRenewalCatchUpInvoiceIfEligible(contract, novRenewal)).isEmpty();

    // Active contract with renewal not starting on nextMonthFirstDay -> empty
    contract.setStatus(ContractStatus.ACTIVE);
    ContractRenewal wrongEffectiveRenewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(contract)
            .previousEndOn(LocalDate.of(2026, 11, 30))
            .effectiveFrom(LocalDate.of(2027, 1, 1))
            .newEndOn(LocalDate.of(2027, 5, 31))
            .monthlyRent(new BigDecimal("1750000"))
            .build();
    assertThat(nov26Service.issueRenewalCatchUpInvoiceIfEligible(contract, wrongEffectiveRenewal))
        .isEmpty();

    // Active contract with newEndOn before nextMonthFirstDay -> empty
    ContractRenewal invalidEndRenewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(contract)
            .previousEndOn(LocalDate.of(2026, 11, 30))
            .effectiveFrom(LocalDate.of(2026, 12, 1))
            .newEndOn(LocalDate.of(2026, 11, 30))
            .monthlyRent(new BigDecimal("1750000"))
            .build();
    assertThat(nov26Service.issueRenewalCatchUpInvoiceIfEligible(contract, invalidEndRenewal))
        .isEmpty();

    verifyNoInteractions(invoiceRepository);
  }

  @Test
  @DisplayName(
      "issueRenewalCatchUpInvoiceIfEligible throws on null args or contract/renewal mismatch")
  void issueRenewalCatchUp_invalidArgsOrMismatch_throws() {
    InvoiceIssuanceServiceImpl service = createServiceAtDormLocalTime(2026, 11, 25, 10, 0);
    Contract contract = activeContract();
    Contract otherContract = activeContract();
    Contract nullIdContract = activeContract();
    nullIdContract.setId(null);

    ContractRenewal renewalForOther =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(otherContract)
            .previousEndOn(LocalDate.of(2026, 11, 30))
            .effectiveFrom(LocalDate.of(2026, 12, 1))
            .newEndOn(LocalDate.of(2027, 5, 31))
            .monthlyRent(new BigDecimal("1750000"))
            .build();

    assertThatThrownBy(() -> service.issueRenewalCatchUpInvoiceIfEligible(null, renewalForOther))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.issueRenewalCatchUpInvoiceIfEligible(nullIdContract, renewalForOther))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.issueRenewalCatchUpInvoiceIfEligible(contract, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.issueRenewalCatchUpInvoiceIfEligible(contract, renewalForOther))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(invoiceRepository);
  }
}
