package com.uit.dormmanagement.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.repositories.ContractRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ContractStatusSchedulerTest {

  @Mock private ContractRepository contractRepository;

  @Test
  @DisplayName(
      "updateExpiringContracts: computes thresholdDate in Asia/Ho_Chi_Minh via Clock, queries effective end date, and never mutates Contract.status")
  void updateExpiringContracts_usesDormZoneClockAndEffectiveEndDateReadOnly() {
    // 2027-07-31T17:30:00Z == 2027-08-01T00:30:00+07:00 -> +30 days = 2027-08-31 (not 2027-08-30)
    Clock utcBoundaryClock = Clock.fixed(Instant.parse("2027-07-31T17:30:00Z"), ZoneOffset.UTC);
    ContractStatusScheduler scheduler =
        new ContractStatusScheduler(contractRepository, utcBoundaryClock);

    Contract activeContract =
        Contract.builder()
            .id(UUID.randomUUID())
            .contractCode("HD-2026-0001")
            .startOn(LocalDate.of(2026, 9, 1))
            .originalEndOn(LocalDate.of(2027, 8, 31))
            .status(ContractStatus.ACTIVE)
            .build();

    LocalDate expectedThreshold = LocalDate.of(2027, 8, 31);
    when(contractRepository.findAllByStatusAndEffectiveEndOnLessThanEqual(
            ContractStatus.ACTIVE, expectedThreshold))
        .thenReturn(List.of(activeContract));

    scheduler.updateExpiringContracts();

    verify(contractRepository)
        .findAllByStatusAndEffectiveEndOnLessThanEqual(ContractStatus.ACTIVE, expectedThreshold);
    verify(contractRepository, never()).findAllByStatusAndOriginalEndOnLessThanEqual(any(), any());
    verify(contractRepository, never()).save(any());
    verify(contractRepository, never()).saveAndFlush(any());
    assertThat(activeContract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(activeContract.getCheckedOutAt()).isNull();
  }

  @Test
  @DisplayName("updateExpiringContracts: declares Asia/Ho_Chi_Minh cron zone and readOnly tx")
  void updateExpiringContracts_hasDormZoneScheduleAndReadOnlyTransaction() throws Exception {
    Method method = ContractStatusScheduler.class.getMethod("updateExpiringContracts");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);
    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.zone()).isEqualTo(TimeConfig.DORM_ZONE.getId());
    assertThat(transactional).isNotNull();
    assertThat(transactional.readOnly()).isTrue();
  }
}
