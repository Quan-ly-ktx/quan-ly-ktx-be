package com.uit.dormmanagement.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.PaymentTransactionRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.security.JwtService;
import com.uit.dormmanagement.services.RoomService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "app.vietqr.bank-bin=970405",
      "app.vietqr.account-number=1234567890",
      "app.vietqr.account-name=TEST KTX HUB"
    })
@Import(InvoiceSecurityIntegrationTest.TestClockConfig.class)
class InvoiceSecurityIntegrationTest {

  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};
  private static final byte[] VALID_JPEG_BYTES =
      new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
  private static final byte[] VALID_WEBP_BYTES =
      new byte[] {
        'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
      };

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
        Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), TimeConfig.DORM_ZONE);

    void reset() {
      this.delegate = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), TimeConfig.DORM_ZONE);
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

  @Autowired private MockMvc mockMvc;
  @Autowired private MutableTestClock testClock;
  @Autowired private JwtService jwtService;
  @Autowired private UserTokenInvalidationService userTokenInvalidationService;
  @Autowired private RoomService roomService;
  @Autowired private BedRepository bedRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;
  @Autowired private InMemoryPrivateObjectStorage privateObjectStorage;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User managerUser;
  private StaffProfile managerStaff;
  private User technicianUser;
  private User activeTenantUser;
  private User otherTenantUser;
  private User checkedOutTenantUser;

  private Invoice activeOverdueDebtInvoice;
  private Invoice activePaidSepInvoice;
  private Invoice otherInTermDebtInvoice;
  private Invoice checkedOutDebtInvoice;

  @BeforeEach
  void setUp() {
    testClock.reset();
    privateObjectStorage.clear();

    var room =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.950",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(room.id());

    managerUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("inv_sec_manager@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    managerStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Manager Billing Sec")
                .phone("0988112233")
                .build());

    technicianUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("inv_sec_tech@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());

    activeTenantUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("inv_sec_tenant1@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant activeTenant =
        tenantRepository.saveAndFlush(
            Tenant.builder()
                .user(activeTenantUser)
                .tenantCode("NT-INV-01")
                .fullName("Nguyen Active One")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079205111111")
                .phone("0911550001")
                .permanentAddress("Thu Duc")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    otherTenantUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("inv_sec_tenant2@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant otherTenant =
        tenantRepository.saveAndFlush(
            Tenant.builder()
                .user(otherTenantUser)
                .tenantCode("NT-INV-02")
                .fullName("Tran Other Two")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079205222222")
                .phone("0911550002")
                .permanentAddress("Quan 1")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    checkedOutTenantUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("inv_sec_tenant3@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant checkedOutTenant =
        tenantRepository.saveAndFlush(
            Tenant.builder()
                .user(checkedOutTenantUser)
                .tenantCode("NT-INV-03")
                .fullName("Le Checked Out")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079205333333")
                .phone("0911550003")
                .permanentAddress("Quan 3")
                .residentStatus(ResidentStatus.CHECKED_OUT)
                .build());

    Contract activeContract =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode("HD-2026-9501")
                .tenant(activeTenant)
                .bed(beds.get(0))
                .roomCodeSnapshot("P.950")
                .bedCodeSnapshot(beds.get(0).getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    Contract otherContract =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode("HD-2026-9502")
                .tenant(otherTenant)
                .bed(beds.get(1))
                .roomCodeSnapshot("P.950")
                .bedCodeSnapshot(beds.get(1).getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1600000"))
                .depositAmount(new BigDecimal("1600000"))
                .status(ContractStatus.ACTIVE)
                .build());

    Contract liquidatedContract =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode("HD-2026-9503")
                .tenant(checkedOutTenant)
                .bed(beds.get(2))
                .roomCodeSnapshot("P.950")
                .bedCodeSnapshot(beds.get(2).getBedCode())
                .startOn(LocalDate.of(2026, 8, 1))
                .originalEndOn(LocalDate.of(2027, 1, 31))
                .originalMonthlyRent(new BigDecimal("1400000"))
                .depositAmount(new BigDecimal("1400000"))
                .status(ContractStatus.LIQUIDATED)
                .checkedOutAt(Instant.parse("2026-10-08T09:00:00Z"))
                .build());

    activeOverdueDebtInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-HD-2026-9501")
                .contract(activeContract)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.DEBT)
                .build());

    activePaidSepInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202609-HD-2026-9501")
                .contract(activeContract)
                .billingPeriod(LocalDate.of(2026, 9, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 9, 5))
                .status(InvoiceStatus.PAID)
                .build());
    paymentTransactionRepository.saveAndFlush(
        PaymentTransaction.builder()
            .invoice(activePaidSepInvoice)
            .receiptCode("PT-INV-202609-HD-2026-9501")
            .amount(new BigDecimal("1500000"))
            .paymentMethod(PaymentMethod.CASH)
            .confirmedByStaff(managerStaff)
            .build());

    otherInTermDebtInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-HD-2026-9502")
                .contract(otherContract)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1600000"))
                .dueOn(LocalDate.of(2026, 10, 10))
                .status(InvoiceStatus.DEBT)
                .build());

    checkedOutDebtInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-HD-2026-9503")
                .contract(liquidatedContract)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1400000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.DEBT)
                .build());
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    privateObjectStorage.clear();
    jdbcTemplate.execute(
        "TRUNCATE TABLE payment_transactions, invoices, contract_documents, contract_renewals, "
            + "contracts, room_code_registry, beds, room_amenities, rooms, tenants, staff_profiles, users CASCADE");
  }

  private String tokenFor(User user) {
    String gen = userTokenInvalidationService.getOrCreateGeneration(user.getId());
    return jwtService.generateAccessToken(user, UUID.randomUUID().toString(), gen);
  }

  @Test
  @DisplayName(
      "RBAC matrix: all 12 endpoints (+ HEAD proof) enforce 401 anonymous, 403 TECHNICIAN, and 403 cross-role")
  void rbacMatrix_enforces401And403AcrossAll12Endpoints() throws Exception {
    UUID targetId = activeOverdueDebtInvoice.getId();
    String managerToken = tokenFor(managerUser);
    String tenantToken = tokenFor(activeTenantUser);
    String techToken = tokenFor(technicianUser);
    MockMultipartFile png =
        new MockMultipartFile("file", "proof.png", "image/png", VALID_PNG_BYTES);

    // 1. Anonymous -> 401 on all 8 admin + 4 tenant (+ 2 HEAD)
    mockMvc.perform(get("/api/v1/admin/invoices")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/admin/invoices/summary")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/admin/invoices/{id}", targetId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/v1/admin/invoices/issue-batch"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/admin/invoices/{id}/proof/content", targetId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(head("/api/v1/admin/invoices/{id}/proof/content", targetId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/confirm-cash", targetId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/confirm-transfer", targetId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/reject-proof", targetId))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/v1/tenant/invoices")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/tenant/invoices/{id}", targetId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(multipart("/api/v1/tenant/invoices/{id}/proof", targetId).file(png))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/tenant/invoices/{id}/proof/content", targetId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(head("/api/v1/tenant/invoices/{id}/proof/content", targetId))
        .andExpect(status().isUnauthorized());

    // 2. TECHNICIAN -> 403 on all 12 endpoints (+ 2 HEAD)
    mockMvc
        .perform(get("/api/v1/admin/invoices").header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/summary").header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/{id}", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/admin/invoices/issue-batch")
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            head("/api/v1/admin/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/confirm-cash", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/confirm-transfer", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/reject-proof", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/tenant/invoices").header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            multipart("/api/v1/tenant/invoices/{id}/proof", targetId)
                .file(png)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            head("/api/v1/tenant/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());

    // 3. Cross-role: TENANT -> 403 on all 8 admin endpoints (+ HEAD)
    mockMvc
        .perform(get("/api/v1/admin/invoices").header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/summary").header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/{id}", targetId)
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/admin/invoices/issue-batch")
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            head("/api/v1/admin/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/confirm-cash", targetId)
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/confirm-transfer", targetId)
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/reject-proof", targetId)
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());

    // 4. Cross-role: MANAGER -> 403 on all 4 tenant endpoints (+ HEAD)
    mockMvc
        .perform(get("/api/v1/tenant/invoices").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}", targetId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            multipart("/api/v1/tenant/invoices/{id}/proof", targetId)
                .file(png)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            head("/api/v1/tenant/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "Tenant ownership & post-checkout: ACTIVE and CHECKED_OUT tenants can read/submit/stream own invoices; cross-tenant receives 404")
  void tenantOwnershipAndCheckedOutAccess_enforcesIsolationAndAllowsOwners() throws Exception {
    String activeToken = tokenFor(activeTenantUser);
    String otherToken = tokenFor(otherTenantUser);
    String checkedOutToken = tokenFor(checkedOutTenantUser);

    // ACTIVE tenant lists own invoices (2 invoices: Oct DEBT + Sep PAID)
    mockMvc
        .perform(get("/api/v1/tenant/invoices").header("Authorization", "Bearer " + activeToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].invoiceCode").value("INV-202610-HD-2026-9501"))
        .andExpect(jsonPath("$.data[1].invoiceCode").value("INV-202609-HD-2026-9501"));

    // CHECKED_OUT tenant lists own invoice, views detail with VietQR, submits JPG proof, and
    // streams GET + HEAD
    mockMvc
        .perform(
            get("/api/v1/tenant/invoices").header("Authorization", "Bearer " + checkedOutToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].invoiceCode").value("INV-202610-HD-2026-9503"));

    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}", checkedOutDebtInvoice.getId())
                .header("Authorization", "Bearer " + checkedOutToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DEBT"))
        .andExpect(jsonPath("$.data.vietQr.bankBin").value("970405"))
        .andExpect(jsonPath("$.data.vietQr.transferContent").value("KTX INV-202610-HD-2026-9503"));

    MockMultipartFile jpgFile =
        new MockMultipartFile("file", "checkout-proof.jpg", "image/jpeg", VALID_JPEG_BYTES);
    mockMvc
        .perform(
            multipart("/api/v1/tenant/invoices/{id}/proof", checkedOutDebtInvoice.getId())
                .file(jpgFile)
                .header("Authorization", "Bearer " + checkedOutToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"))
        .andExpect(
            jsonPath("$.data.proof.contentUrl")
                .value(
                    "/api/v1/tenant/invoices/" + checkedOutDebtInvoice.getId() + "/proof/content"));

    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}/proof/content", checkedOutDebtInvoice.getId())
                .header("Authorization", "Bearer " + checkedOutToken))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"INV-202610-HD-2026-9503-proof.jpg\""))
        .andExpect(content().bytes(VALID_JPEG_BYTES));

    mockMvc
        .perform(
            head("/api/v1/tenant/invoices/{id}/proof/content", checkedOutDebtInvoice.getId())
                .header("Authorization", "Bearer " + checkedOutToken))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
        .andExpect(
            header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(VALID_JPEG_BYTES.length)))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"INV-202610-HD-2026-9503-proof.jpg\""))
        .andExpect(content().bytes(new byte[0]));

    // Cross-tenant isolation -> 404 INVOICE_404 across view, submit, GET stream, and HEAD stream
    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}", checkedOutDebtInvoice.getId())
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("INVOICE_404"));

    mockMvc
        .perform(
            multipart("/api/v1/tenant/invoices/{id}/proof", activeOverdueDebtInvoice.getId())
                .file(jpgFile)
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("INVOICE_404"));

    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}/proof/content", checkedOutDebtInvoice.getId())
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            head("/api/v1/tenant/invoices/{id}/proof/content", checkedOutDebtInvoice.getId())
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "VietQR & privacy hygiene: DEBT exposes valid EMVCo CRC-16 VietQR; PAID/PENDING omit VietQR; responses never leak S3 keys or /uploads/")
  void vietQrAndPrivacyHygiene_validatesEmvCoCrcAndRedactsKeys() throws Exception {
    String managerToken = tokenFor(managerUser);
    String tenantToken = tokenFor(activeTenantUser);

    // 1. DEBT invoice has valid VietQR on both admin & tenant views
    String tenantDebtJson =
        mockMvc
            .perform(
                get("/api/v1/tenant/invoices/{id}", activeOverdueDebtInvoice.getId())
                    .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.vietQr.bankBin").value("970405"))
            .andExpect(jsonPath("$.data.vietQr.bankName").value("Agribank"))
            .andExpect(jsonPath("$.data.vietQr.accountNumber").value("1234567890"))
            .andExpect(jsonPath("$.data.vietQr.accountName").value("TEST KTX HUB"))
            .andExpect(jsonPath("$.data.vietQr.amount").value(1500000))
            .andExpect(
                jsonPath("$.data.vietQr.transferContent").value("KTX INV-202610-HD-2026-9501"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String qrPayload =
        com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .build()
            .readTree(tenantDebtJson)
            .path("data")
            .path("vietQr")
            .path("qrPayload")
            .asText();
    assertValidEmvCoCrc16(qrPayload);

    mockMvc
        .perform(
            get("/api/v1/admin/invoices/{id}", activeOverdueDebtInvoice.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.vietQr.qrPayload").value(qrPayload));

    // 2. PAID invoice has null vietQr
    mockMvc
        .perform(
            get("/api/v1/tenant/invoices/{id}", activePaidSepInvoice.getId())
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.vietQr").value(org.hamcrest.Matchers.nullValue()));

    // 3. Submit proof -> PENDING_CONFIRMATION has null vietQr and never exposes raw S3 keys
    MockMultipartFile pngFile =
        new MockMultipartFile("file", "secret-local-name.png", "image/png", VALID_PNG_BYTES);
    String submitJson =
        mockMvc
            .perform(
                multipart("/api/v1/tenant/invoices/{id}/proof", activeOverdueDebtInvoice.getId())
                    .file(pngFile)
                    .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"))
            .andExpect(jsonPath("$.data.vietQr").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String rawProofKey =
        invoiceRepository
            .findById(activeOverdueDebtInvoice.getId())
            .orElseThrow()
            .getProofFileKey();
    assertThat(rawProofKey).isNotBlank();

    String adminListJson =
        mockMvc
            .perform(
                get("/api/v1/admin/invoices").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String adminDetailJson =
        mockMvc
            .perform(
                get("/api/v1/admin/invoices/{id}", activeOverdueDebtInvoice.getId())
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String tenantListJson =
        mockMvc
            .perform(
                get("/api/v1/tenant/invoices").header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (String json : List.of(submitJson, adminListJson, adminDetailJson, tenantListJson)) {
      assertThat(json)
          .doesNotContain(rawProofKey)
          .doesNotContain("proof_file_key")
          .doesNotContain("proofFileKey")
          .doesNotContain("/uploads/")
          .doesNotContain("secret-local-name");
    }

    // 4. Proof streaming sets safe synthetic Content-Disposition filename
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/{id}/proof/content", activeOverdueDebtInvoice.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"INV-202610-HD-2026-9501-proof.png\""))
        .andExpect(content().bytes(VALID_PNG_BYTES));
  }

  @Test
  @DisplayName(
      "Full HTTP reconciliation lifecycle, admin filters, batch issuance, and summary totals")
  void httpReconciliationFlow_updatesStatusesAndSummaryTotals() throws Exception {
    String managerToken = tokenFor(managerUser);
    String tenantToken = tokenFor(activeTenantUser);
    UUID targetId = activeOverdueDebtInvoice.getId();

    // 1. Initial admin summary & filtered search
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/summary")
                .param("billingPeriod", "2026-10-01")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paidAmount").value(0))
        .andExpect(jsonPath("$.data.paidCount").value(0))
        .andExpect(jsonPath("$.data.unpaidInTermAmount").value(1600000))
        .andExpect(jsonPath("$.data.unpaidInTermCount").value(1))
        .andExpect(jsonPath("$.data.overdueAmount").value(2900000))
        .andExpect(jsonPath("$.data.overdueCount").value(2))
        .andExpect(jsonPath("$.data.pendingConfirmationAmount").value(0))
        .andExpect(jsonPath("$.data.pendingConfirmationCount").value(0))
        .andExpect(jsonPath("$.data.totalExpectedAmount").value(4500000))
        .andExpect(jsonPath("$.data.totalInvoiceCount").value(3));

    mockMvc
        .perform(
            get("/api/v1/admin/invoices/summary")
                .param("billingPeriod", "2026-10-15")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVOICE_400_INVALID_PERIOD"));

    mockMvc
        .perform(
            get("/api/v1/admin/invoices")
                .param("overdueOnly", "true")
                .param("search", "Active One")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].invoiceCode").value("INV-202610-HD-2026-9501"))
        .andExpect(jsonPath("$.data.items[0].overdue").value(true))
        .andExpect(jsonPath("$.data.items[0].overdueDays").value(5));

    // 2. Submit proof -> PENDING_CONFIRMATION (moves out of overdue into pendingConfirmation)
    MockMultipartFile png =
        new MockMultipartFile("file", "first.png", "image/png", VALID_PNG_BYTES);
    mockMvc
        .perform(
            multipart("/api/v1/tenant/invoices/{id}/proof", targetId)
                .file(png)
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"))
        .andExpect(jsonPath("$.data.overdue").value(false));

    mockMvc
        .perform(
            get("/api/v1/admin/invoices/summary")
                .param("billingPeriod", "2026-10-01")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.overdueAmount").value(1400000))
        .andExpect(jsonPath("$.data.overdueCount").value(1))
        .andExpect(jsonPath("$.data.pendingConfirmationAmount").value(1500000))
        .andExpect(jsonPath("$.data.pendingConfirmationCount").value(1));

    // 3. Attempt cash confirm on PENDING_CONFIRMATION -> 409 INVOICE_409_NOT_DEBT
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/confirm-cash", targetId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("INVOICE_409_NOT_DEBT"));

    // 4. Reject proof -> back to DEBT with VietQR, old proof removed
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/reject-proof", targetId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DEBT"))
        .andExpect(jsonPath("$.data.proofAvailable").value(false))
        .andExpect(jsonPath("$.data.vietQr.transferContent").value("KTX INV-202610-HD-2026-9501"));

    mockMvc
        .perform(
            get("/api/v1/admin/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("INVOICE_404_PROOF"));

    // 5. Resubmit WEBP proof -> PENDING_CONFIRMATION, then confirm transfer -> PAID
    MockMultipartFile webp =
        new MockMultipartFile("file", "resubmit.webp", "image/webp", VALID_WEBP_BYTES);
    mockMvc
        .perform(
            multipart("/api/v1/tenant/invoices/{id}/proof", targetId)
                .file(webp)
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"));

    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/confirm-transfer", targetId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.payment.paymentMethod").value("VIETQR_TRANSFER"))
        .andExpect(jsonPath("$.data.payment.receiptCode").value("PT-INV-202610-HD-2026-9501"))
        .andExpect(jsonPath("$.data.vietQr").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.proofAvailable").value(true));

    // Proof remains streamable for audit after transfer confirmation
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/{id}/proof/content", targetId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"INV-202610-HD-2026-9501-proof.webp\""))
        .andExpect(content().bytes(VALID_WEBP_BYTES));

    // 6. Confirm cash payment on another DEBT invoice -> PAID (CASH)
    mockMvc
        .perform(
            patch("/api/v1/admin/invoices/{id}/confirm-cash", otherInTermDebtInvoice.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.payment.paymentMethod").value("CASH"));

    // 7. Summary reflects updated paid & remaining overdue totals
    mockMvc
        .perform(
            get("/api/v1/admin/invoices/summary")
                .param("billingPeriod", "2026-10-01")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paidAmount").value(3100000))
        .andExpect(jsonPath("$.data.paidCount").value(2))
        .andExpect(jsonPath("$.data.unpaidInTermAmount").value(0))
        .andExpect(jsonPath("$.data.unpaidInTermCount").value(0))
        .andExpect(jsonPath("$.data.overdueAmount").value(1400000))
        .andExpect(jsonPath("$.data.overdueCount").value(1))
        .andExpect(jsonPath("$.data.pendingConfirmationAmount").value(0))
        .andExpect(jsonPath("$.data.pendingConfirmationCount").value(0))
        .andExpect(jsonPath("$.data.totalExpectedAmount").value(4500000))
        .andExpect(jsonPath("$.data.totalInvoiceCount").value(3));

    // 8. Empty-body issue-batch succeeds idempotently for current billing period (2026-10-01)
    mockMvc
        .perform(
            post("/api/v1/admin/invoices/issue-batch")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.billingPeriod").value("2026-10-01"))
        .andExpect(jsonPath("$.data.eligibleContractCount").value(2))
        .andExpect(jsonPath("$.data.issuedCount").value(0))
        .andExpect(jsonPath("$.data.skippedExistingCount").value(2));
  }

  private static void assertValidEmvCoCrc16(String qrPayload) {
    assertThat(qrPayload).isNotNull().hasSizeGreaterThan(8);
    int crcTagIdx = qrPayload.length() - 8;
    assertThat(qrPayload.substring(crcTagIdx, crcTagIdx + 4)).isEqualTo("6304");
    String dataToCrc = qrPayload.substring(0, qrPayload.length() - 4);
    String actualCrc = qrPayload.substring(qrPayload.length() - 4);
    int crc = 0xFFFF;
    for (byte b : dataToCrc.getBytes(StandardCharsets.UTF_8)) {
      crc ^= (b & 0xFF) << 8;
      for (int bit = 0; bit < 8; bit++) {
        if ((crc & 0x8000) != 0) {
          crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
        } else {
          crc = (crc << 1) & 0xFFFF;
        }
      }
    }
    assertThat(actualCrc).isEqualTo(String.format("%04X", crc & 0xFFFF));
  }
}
