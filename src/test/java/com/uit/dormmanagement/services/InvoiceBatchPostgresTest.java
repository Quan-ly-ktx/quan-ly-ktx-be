package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
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
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.schedulers.MonthlyInvoiceIssuanceScheduler;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(InvoiceBatchPostgresTest.TestClockConfig.class)
class InvoiceBatchPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.baseline-on-migrate", () -> false);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    MutableTestClock testClock() {
      return new MutableTestClock();
    }
  }

  static class MutableTestClock extends Clock {
    private volatile Clock delegate =
        Clock.fixed(Instant.parse("2027-01-24T17:05:00Z"), TimeConfig.DORM_ZONE);

    void setClock(Clock clock) {
      this.delegate = clock;
    }

    void reset() {
      this.delegate = Clock.fixed(Instant.parse("2027-01-24T17:05:00Z"), TimeConfig.DORM_ZONE);
    }

    @Override
    public ZoneId getZone() {
      return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return delegate.withZone(zone);
    }

    @Override
    public Instant instant() {
      return delegate.instant();
    }
  }

  @Autowired private MutableTestClock testClock;
  @Autowired private InvoiceBatchService invoiceBatchService;
  @Autowired private InvoiceIssuanceService invoiceIssuanceService;
  @Autowired private MonthlyInvoiceIssuanceScheduler monthlyInvoiceIssuanceScheduler;
  @Autowired private ScheduledTaskHolder scheduledTaskHolder;
  @Autowired private RoomService roomService;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractRenewalRepository contractRenewalRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID roomId;
  private List<Bed> roomBeds;

  @BeforeEach
  void setUp() {
    testClock.reset();
    var roomResp =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.920",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    roomId = roomResp.id();
    roomBeds = bedRepository.findByRoomIdOrderByBedCodeAsc(roomId);
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    jdbcTemplate.execute(
        "TRUNCATE TABLE payment_transactions, invoices, contract_documents, contract_renewals, "
            + "contracts, room_code_registry, beds, room_amenities, rooms, tenants, users CASCADE");
  }

  private Tenant createTenant(String suffix, String idCard, ResidentStatus residentStatus) {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .email("batch." + suffix + "@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    return tenantRepository.saveAndFlush(
        Tenant.builder()
            .user(user)
            .tenantCode("NT-BATCH-" + suffix)
            .fullName("Nguyen Van " + suffix)
            .gender(GenderPolicy.MALE)
            .idCardNumber(idCard)
            .phone("090199" + idCard.substring(6))
            .permanentAddress("Thu Duc, TP.HCM")
            .residentStatus(residentStatus)
            .build());
  }

  private Contract createContract(
      String contractCode,
      Tenant tenant,
      Bed bed,
      LocalDate startOn,
      LocalDate originalEndOn,
      BigDecimal originalMonthlyRent,
      ContractStatus status,
      Instant checkedOutAt) {
    return contractRepository.saveAndFlush(
        Contract.builder()
            .contractCode(contractCode)
            .tenant(tenant)
            .bed(bed)
            .roomCodeSnapshot("P.920")
            .bedCodeSnapshot(bed.getBedCode())
            .startOn(startOn)
            .originalEndOn(originalEndOn)
            .originalMonthlyRent(originalMonthlyRent)
            .depositAmount(originalMonthlyRent)
            .status(status)
            .checkedOutAt(checkedOutAt)
            .build());
  }

  @Test
  @DisplayName(
      "End-to-end batch issues exact locked rent across original and multi-renewal contracts, excludes liquidated/expired, is idempotent, and registers day-25 cron")
  void issueMonthlyBatch_endToEndMultiContractAndIdempotent() {
    Tenant tenantA = createTenant("a", "079203920001", ResidentStatus.ACTIVE);
    Tenant tenantB = createTenant("b", "079203920002", ResidentStatus.ACTIVE);
    Tenant tenantC = createTenant("c", "079203920003", ResidentStatus.CHECKED_OUT);
    Tenant tenantD = createTenant("d", "079203920004", ResidentStatus.ACTIVE);

    // Contract A: ACTIVE original term covering 2027-02-01 at 1,500,000
    Contract contractA =
        createContract(
            "HD-2026-9201",
            tenantA,
            roomBeds.get(0),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1500000"),
            ContractStatus.ACTIVE,
            null);

    // Contract B: ACTIVE with 2 renewals -> seq 1 covers 2027-02-01 at 1,800,000; seq 2 is future
    // 2027-07-01..2027-12-31 at 2,000,000
    Contract contractB =
        createContract(
            "HD-2026-9202",
            tenantB,
            roomBeds.get(1),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 12, 31),
            new BigDecimal("1500000"),
            ContractStatus.ACTIVE,
            null);
    contractRenewalRepository.saveAndFlush(
        ContractRenewal.builder()
            .contract(contractB)
            .sequenceNo(1)
            .signedOn(LocalDate.of(2026, 12, 10))
            .previousEndOn(LocalDate.of(2026, 12, 31))
            .effectiveFrom(LocalDate.of(2027, 1, 1))
            .newEndOn(LocalDate.of(2027, 6, 30))
            .monthlyRent(new BigDecimal("1800000"))
            .build());
    contractRenewalRepository.saveAndFlush(
        ContractRenewal.builder()
            .contract(contractB)
            .sequenceNo(2)
            .signedOn(LocalDate.of(2027, 1, 10))
            .previousEndOn(LocalDate.of(2027, 6, 30))
            .effectiveFrom(LocalDate.of(2027, 7, 1))
            .newEndOn(LocalDate.of(2027, 12, 31))
            .monthlyRent(new BigDecimal("2000000"))
            .build());

    // Contract C: LIQUIDATED covering dates -> must be excluded
    Contract contractC =
        createContract(
            "HD-2026-9203",
            tenantC,
            roomBeds.get(2),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 5, 31),
            new BigDecimal("1500000"),
            ContractStatus.LIQUIDATED,
            Instant.parse("2027-01-15T10:00:00Z"));

    // Contract D: ACTIVE expired 2027-01-31 with no renewals -> must be excluded for 2027-02-01
    Contract contractD =
        createContract(
            "HD-2026-9204",
            tenantD,
            roomBeds.get(3),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2027, 1, 31),
            new BigDecimal("1500000"),
            ContractStatus.ACTIVE,
            null);

    // Raise room.basePrice after contract/renewal signing; batch must NEVER use room.basePrice
    Room room = roomRepository.findById(roomId).orElseThrow();
    room.setBasePrice(new BigDecimal("2200000"));
    roomRepository.saveAndFlush(room);

    // Clock = 2027-01-25 00:05+07:00
    LocalDate targetPeriod = LocalDate.of(2027, 2, 1);
    InvoiceBatchIssuanceResponse firstRun = invoiceBatchService.issueMonthlyBatch(targetPeriod);

    assertThat(firstRun.billingPeriod()).isEqualTo(targetPeriod);
    assertThat(firstRun.eligibleContractCount()).isEqualTo(2);
    assertThat(firstRun.issuedCount()).isEqualTo(2);
    assertThat(firstRun.skippedExistingCount()).isZero();
    assertThat(firstRun.issuedInvoices()).hasSize(2);

    InvoiceSummaryResponse summaryB =
        firstRun.issuedInvoices().stream()
            .filter(i -> i.contractId().equals(contractB.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(summaryB.invoiceCode()).isEqualTo("INV-202702-HD-2026-9202");
    assertThat(summaryB.amount()).isEqualByComparingTo("1800000");
    assertThat(summaryB.dueOn()).isEqualTo(LocalDate.of(2027, 2, 5));
    assertThat(summaryB.status()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(summaryB.overdue()).isFalse();
    assertThat(summaryB.overdueDays()).isZero();
    assertThat(summaryB.roomNumber()).isEqualTo("P.920");
    assertThat(summaryB.bedCode()).isEqualTo(roomBeds.get(1).getBedCode());
    assertThat(summaryB.tenantEmail()).isEqualTo("batch.b@uit.edu.vn");
    assertThat(summaryB.payment()).isNull();

    InvoiceSummaryResponse summaryA =
        firstRun.issuedInvoices().stream()
            .filter(i -> i.contractId().equals(contractA.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(summaryA.invoiceCode()).isEqualTo("INV-202702-HD-2026-9201");
    assertThat(summaryA.amount()).isEqualByComparingTo("1500000");
    assertThat(summaryA.dueOn()).isEqualTo(LocalDate.of(2027, 2, 5));
    assertThat(summaryA.status()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(summaryA.overdue()).isFalse();
    assertThat(summaryA.overdueDays()).isZero();

    assertThat(invoiceRepository.findByContractId(contractC.getId())).isEmpty();
    assertThat(invoiceRepository.findByContractId(contractD.getId())).isEmpty();

    // Re-running batch must be 100% idempotent
    InvoiceBatchIssuanceResponse secondRun = invoiceBatchService.issueMonthlyBatch(targetPeriod);
    assertThat(secondRun.eligibleContractCount()).isEqualTo(2);
    assertThat(secondRun.issuedCount()).isZero();
    assertThat(secondRun.skippedExistingCount()).isEqualTo(2);
    assertThat(secondRun.issuedInvoices()).isEmpty();

    // Scheduler invocation at 2027-01-25 00:05+07:00 is also idempotent and leaves exact 1 row each
    monthlyInvoiceIssuanceScheduler.issueNextMonthInvoices();
    assertThat(invoiceRepository.findByContractId(contractA.getId())).hasSize(1);
    assertThat(invoiceRepository.findByContractId(contractB.getId())).hasSize(1);
    assertThat(invoiceRepository.findByContractId(contractC.getId())).isEmpty();
    assertThat(invoiceRepository.findByContractId(contractD.getId())).isEmpty();

    // Verify CronTask registration in Spring ScheduledTaskHolder
    boolean hasDay25Cron =
        scheduledTaskHolder.getScheduledTasks().stream()
            .map(task -> task.getTask())
            .filter(CronTask.class::isInstance)
            .map(CronTask.class::cast)
            .anyMatch(
                cronTask ->
                    cronTask.getExpression().equals("0 0 0 25 * *")
                        && cronTask.toString().contains("MonthlyInvoiceIssuanceScheduler"));
    assertThat(hasDay25Cron).isTrue();
  }

  @Test
  @DisplayName(
      "Deterministic PostgreSQL 23505 (uq_invoices_contract_period) rolls back ONLY failing REQUIRES_NEW sub-tx without 25P02 abort on Contract B")
  void issueMonthlyBatch_uniqueRaceOnContractA_isolatesRollbackAndIssuesContractB()
      throws Exception {
    Tenant tenantA = createTenant("racea", "079203920011", ResidentStatus.ACTIVE);
    Tenant tenantB = createTenant("raceb", "079203920012", ResidentStatus.ACTIVE);

    Contract contractA =
        createContract(
            "HD-2026-9301",
            tenantA,
            roomBeds.get(0),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1500000"),
            ContractStatus.ACTIVE,
            null);
    Contract contractB =
        createContract(
            "HD-2026-9302",
            tenantB,
            roomBeds.get(1),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1600000"),
            ContractStatus.ACTIVE,
            null);

    LocalDate targetPeriod = LocalDate.of(2027, 2, 1);
    TransactionTemplate racerTx = new TransactionTemplate(transactionManager);
    racerTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    CountDownLatch racerInsertedLatch = new CountDownLatch(1);
    CountDownLatch releaseRacerLatch = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<Invoice> racerFuture =
          executor.submit(
              () ->
                  racerTx.execute(
                      status -> {
                        Contract reloadedA =
                            contractRepository.findById(contractA.getId()).orElseThrow();
                        Invoice inserted =
                            invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
                                reloadedA, targetPeriod, new BigDecimal("1500000"));
                        racerInsertedLatch.countDown();
                        try {
                          if (!releaseRacerLatch.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                "Timed out waiting for releaseRacerLatch");
                          }
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(e);
                        }
                        return inserted;
                      }));

      assertThat(racerInsertedLatch.await(5, TimeUnit.SECONDS)).isTrue();

      Future<InvoiceBatchIssuanceResponse> batchFuture =
          executor.submit(() -> invoiceBatchService.issueMonthlyBatch(targetPeriod));

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean sawBlockedBatchInsert = false;
      while (System.nanoTime() < deadline) {
        Integer waitingCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_stat_activity
                WHERE pid <> pg_backend_pid()
                  AND wait_event_type = 'Lock'
                  AND query ILIKE '%invoices%'
                """,
                Integer.class);
        if (waitingCount != null && waitingCount > 0) {
          sawBlockedBatchInsert = true;
          break;
        }
        Thread.sleep(25);
      }
      assertThat(sawBlockedBatchInsert).isTrue();

      releaseRacerLatch.countDown();

      Invoice racerInvoice = racerFuture.get(10, TimeUnit.SECONDS);
      InvoiceBatchIssuanceResponse batchResponse = batchFuture.get(10, TimeUnit.SECONDS);

      assertThat(racerInvoice).isNotNull();
      assertThat(batchResponse.billingPeriod()).isEqualTo(targetPeriod);
      assertThat(batchResponse.eligibleContractCount()).isEqualTo(2);
      assertThat(batchResponse.issuedCount()).isEqualTo(1);
      assertThat(batchResponse.skippedExistingCount()).isEqualTo(1);
      assertThat(batchResponse.issuedInvoices()).hasSize(1);
      assertThat(batchResponse.issuedInvoices().getFirst().contractId())
          .isEqualTo(contractB.getId());

      assertThat(invoiceRepository.findByContractId(contractA.getId())).hasSize(1);
      assertThat(invoiceRepository.findByContractId(contractB.getId())).hasSize(1);
    } finally {
      releaseRacerLatch.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  @DisplayName(
      "Dual concurrent issueMonthlyBatch calls against real PostgreSQL produce zero 25P02 errors and exact one invoice per eligible contract")
  void issueMonthlyBatch_concurrentDualBatchCalls_succeedCleanlyWithOneInvoicePerContract()
      throws Exception {
    Tenant tenantA = createTenant("conca", "079203920021", ResidentStatus.ACTIVE);
    Tenant tenantB = createTenant("concb", "079203920022", ResidentStatus.ACTIVE);
    Tenant tenantC = createTenant("concc", "079203920023", ResidentStatus.CHECKED_OUT);

    Contract contractA =
        createContract(
            "HD-2026-9401",
            tenantA,
            roomBeds.get(0),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1500000"),
            ContractStatus.ACTIVE,
            null);
    Contract contractB =
        createContract(
            "HD-2026-9402",
            tenantB,
            roomBeds.get(1),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1600000"),
            ContractStatus.ACTIVE,
            null);
    Contract contractC =
        createContract(
            "HD-2026-9403",
            tenantC,
            roomBeds.get(2),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2027, 2, 28),
            new BigDecimal("1500000"),
            ContractStatus.LIQUIDATED,
            Instant.parse("2027-01-10T10:00:00Z"));

    LocalDate targetPeriod = LocalDate.of(2027, 2, 1);
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<InvoiceBatchIssuanceResponse> f1 =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return invoiceBatchService.issueMonthlyBatch(targetPeriod);
              });
      Future<InvoiceBatchIssuanceResponse> f2 =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return invoiceBatchService.issueMonthlyBatch(targetPeriod);
              });

      InvoiceBatchIssuanceResponse r1 = f1.get(15, TimeUnit.SECONDS);
      InvoiceBatchIssuanceResponse r2 = f2.get(15, TimeUnit.SECONDS);

      assertThat(r1.eligibleContractCount()).isEqualTo(2);
      assertThat(r2.eligibleContractCount()).isEqualTo(2);
      assertThat(r1.issuedCount() + r1.skippedExistingCount()).isEqualTo(2);
      assertThat(r2.issuedCount() + r2.skippedExistingCount()).isEqualTo(2);
      assertThat(r1.issuedCount() + r2.issuedCount()).isEqualTo(2);

      assertThat(invoiceRepository.findByContractId(contractA.getId())).hasSize(1);
      assertThat(invoiceRepository.findByContractId(contractB.getId())).hasSize(1);
      assertThat(invoiceRepository.findByContractId(contractC.getId())).isEmpty();
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
