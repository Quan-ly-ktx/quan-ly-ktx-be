package com.uit.dormmanagement.schedulers;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.repositories.ContractRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractStatusScheduler {

  private final ContractRepository contractRepository;
  private final Clock clock;

  @Scheduled(cron = "${app.scheduler.contract-status-cron:0 0 0 * * *}", zone = "Asia/Ho_Chi_Minh")
  @Transactional(readOnly = true)
  public void updateExpiringContracts() {
    LocalDate today = LocalDate.now(clock.withZone(TimeConfig.DORM_ZONE));
    LocalDate thresholdDate = today.plusDays(30);
    List<Contract> expiringContracts =
        contractRepository.findAllByStatusAndEffectiveEndOnLessThanEqual(
            ContractStatus.ACTIVE, thresholdDate);

    if (!expiringContracts.isEmpty()) {
      log.info(
          "ContractStatusScheduler: found {} active contract(s) expiring by {}",
          expiringContracts.size(),
          thresholdDate);
    }
  }
}
