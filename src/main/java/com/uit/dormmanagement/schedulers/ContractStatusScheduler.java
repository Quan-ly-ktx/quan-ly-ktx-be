package com.uit.dormmanagement.schedulers;

import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.repositories.ContractRepository;
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

  @Scheduled(cron = "${app.scheduler.contract-status-cron:0 0 0 * * *}")
  @Transactional
  public void updateExpiringContracts() {
    LocalDate thresholdDate = LocalDate.now().plusDays(30);
    List<Contract> expiringContracts =
        contractRepository.findAllByStatusAndEndDateLessThanEqual(
            ContractStatus.ACTIVE, thresholdDate);

    if (!expiringContracts.isEmpty()) {
      for (Contract contract : expiringContracts) {
        contract.setStatus(ContractStatus.EXPIRING_SOON);
      }
      contractRepository.saveAll(expiringContracts);
      log.info(
          "ContractStatusScheduler: updated {} contract(s) to EXPIRING_SOON",
          expiringContracts.size());
    }
  }
}
