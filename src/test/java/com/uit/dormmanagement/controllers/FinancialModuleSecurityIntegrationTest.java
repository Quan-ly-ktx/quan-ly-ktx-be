package com.uit.dormmanagement.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.FacilityExpense;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.MaintenanceTicket;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.TicketReplacementPart;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.TicketCategory;
import com.uit.dormmanagement.enums.TicketPriority;
import com.uit.dormmanagement.enums.TicketStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.FacilityExpenseRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.MaintenanceTicketRepository;
import com.uit.dormmanagement.repositories.PaymentTransactionRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.TicketReplacementPartRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.security.JwtService;
import com.uit.dormmanagement.services.RoomService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.services.impl.FinancialReportExportServiceImpl;
import com.uit.dormmanagement.services.impl.FinancialReportServiceImpl;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(FinancialModuleSecurityIntegrationTest.TestClockConfig.class)
class FinancialModuleSecurityIntegrationTest {

  private static final Instant DEFAULT_CLOCK_INSTANT = Instant.parse("2026-10-20T05:00:00Z");
  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};
  private static final byte[] VALID_PDF_BYTES =
      "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
  private static final String VALID_EXPENSE_JSON =
      """
      {
        "expenseType": "ELECTRICITY",
        "title": "Tien dien RBAC",
        "amount": 500000,
        "expenseDate": "2026-10-10"
      }
      """;

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
    private volatile Clock delegate = Clock.fixed(DEFAULT_CLOCK_INSTANT, TimeConfig.DORM_ZONE);

    void reset() {
      this.delegate = Clock.fixed(DEFAULT_CLOCK_INSTANT, TimeConfig.DORM_ZONE);
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
  @Autowired private InMemoryPrivateObjectStorage privateObjectStorage;
  @Autowired private RoomService roomService;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;
  @Autowired private MaintenanceTicketRepository maintenanceTicketRepository;
  @Autowired private TicketReplacementPartRepository ticketReplacementPartRepository;
  @Autowired private FacilityExpenseRepository facilityExpenseRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private Room room;
  private List<Bed> beds;
  private User managerUser;
  private StaffProfile managerStaff;
  private User technicianUser;
  private StaffProfile techStaff;
  private User tenantUser;
  private Tenant tenant;
  private FacilityExpense baselineExpense;

  @BeforeEach
  void setUp() {
    testClock.reset();
    privateObjectStorage.clear();

    var roomDto =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.970",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    room = roomRepository.findById(roomDto.id()).orElseThrow();
    beds = bedRepository.findByRoomIdOrderByBedCodeAsc(room.getId());

    managerUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("fin_sec_manager@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    managerStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Manager Finance Sec")
                .phone("0988770001")
                .build());

    technicianUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("fin_sec_tech@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());
    techStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(technicianUser)
                .fullName("Technician Finance Sec")
                .phone("0988770002")
                .build());

    tenantUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("fin_sec_tenant@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    tenant =
        tenantRepository.saveAndFlush(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-FIN-SEC-01")
                .fullName("Nguyen Resident Fin")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079205970001")
                .phone("0911970001")
                .permanentAddress("Thu Duc, TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    baselineExpense =
        facilityExpenseRepository.saveAndFlush(
            FacilityExpense.builder()
                .recordedByStaff(managerStaff)
                .expenseDate(LocalDate.of(2026, 9, 15))
                .expenseType(ExpenseType.WATER)
                .title("Nuoc thang 9")
                .amount(new BigDecimal("400000"))
                .invoiceCode("SWC-2026-09")
                .build());
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    privateObjectStorage.clear();
    jdbcTemplate.execute(
        "TRUNCATE TABLE violations, ticket_replacement_parts, maintenance_tickets, facility_expenses, "
            + "payment_transactions, invoices, contract_documents, contract_renewals, contracts, "
            + "room_code_registry, beds, room_amenities, rooms, tenants, staff_profiles, users CASCADE");
  }

  private String tokenFor(User user) {
    String gen = userTokenInvalidationService.getOrCreateGeneration(user.getId());
    return jwtService.generateAccessToken(user, UUID.randomUUID().toString(), gen);
  }

  @Test
  @DisplayName(
      "RBAC matrix: all 10 FR-14 endpoints (+ HEAD) enforce 401 Anonymous, 403 TENANT, and 403 TECHNICIAN")
  void rbacMatrix_enforces401AnonymousAnd403TenantAndTechnicianAcrossAll10EndpointsAndHead()
      throws Exception {
    UUID targetId = baselineExpense.getId();

    performAll10EndpointsAndHead(null, status().isUnauthorized(), targetId);
    performAll10EndpointsAndHead(tokenFor(tenantUser), status().isForbidden(), targetId);
    performAll10EndpointsAndHead(tokenFor(technicianUser), status().isForbidden(), targetId);
  }

  @Test
  @DisplayName(
      "Manager HTTP lifecycle: create, list, detail, update, receipt upload/GET/HEAD/delete, and expense delete")
  void managerHttpLifecycle_createListDetailUpdateUploadStreamDeleteReceiptAndExpense()
      throws Exception {
    String managerToken = tokenFor(managerUser);

    // 1. Create expense -> 201 Created
    String createJson =
        mockMvc
            .perform(
                post("/api/v1/admin/expenses")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "expenseType": "ELECTRICITY",
                          "title": "Tien dien EVN khu A",
                          "amount": 1200000,
                          "expenseDate": "2026-10-12",
                          "invoiceCode": "EVN-970",
                          "notes": "Thanh toan ky 10/2026"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.httpStatus").value(201))
            .andExpect(jsonPath("$.data.expenseType").value("ELECTRICITY"))
            .andExpect(jsonPath("$.data.amount").value(1200000))
            .andExpect(jsonPath("$.data.recordedByStaffName").value("Manager Finance Sec"))
            .andExpect(jsonPath("$.data.receipt").value(Matchers.nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID expenseId =
        UUID.fromString(jsonMapper.readTree(createJson).path("data").path("id").asText());

    // 2. Filtered list -> 200
    String listJson =
        mockMvc
            .perform(
                get("/api/v1/admin/expenses")
                    .param("period", "2026-10")
                    .param("expenseType", "ELECTRICITY")
                    .param("search", "EVN")
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(expenseId.toString()))
            .andExpect(jsonPath("$.data.items[0].amount").value(1200000))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // 3. Detail -> 200
    mockMvc
        .perform(
            get("/api/v1/admin/expenses/{id}", expenseId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(expenseId.toString()))
        .andExpect(jsonPath("$.data.invoiceCode").value("EVN-970"));

    // 4. Update -> 200
    mockMvc
        .perform(
            put("/api/v1/admin/expenses/{id}", expenseId)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "WATER",
                      "title": "Tien nuoc Sawaco khu A",
                      "amount": 950000,
                      "expenseDate": "2026-10-14",
                      "invoiceCode": "SWC-970",
                      "notes": "Da cap nhat"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.expenseType").value("WATER"))
        .andExpect(jsonPath("$.data.title").value("Tien nuoc Sawaco khu A"))
        .andExpect(jsonPath("$.data.amount").value(950000));

    // 5. Upload PNG receipt -> 200
    MockMultipartFile pngFile =
        new MockMultipartFile("file", "secret-receipt-local.png", "image/png", VALID_PNG_BYTES);
    String uploadJson =
        mockMvc
            .perform(
                multipart("/api/v1/admin/expenses/{id}/receipt", expenseId)
                    .file(pngFile)
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.receipt.available").value(true))
            .andExpect(
                jsonPath("$.data.receipt.contentUrl")
                    .value("/api/v1/admin/expenses/" + expenseId + "/receipt/content"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String rawReceiptKey =
        facilityExpenseRepository.findById(expenseId).orElseThrow().getInvoiceFileKey();
    assertThat(rawReceiptKey).isNotBlank();
    assertThat(privateObjectStorage.exists(rawReceiptKey)).isTrue();

    String detailWithReceiptJson =
        mockMvc
            .perform(
                get("/api/v1/admin/expenses/{id}", expenseId)
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.receipt.available").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (String json : List.of(listJson, uploadJson, detailWithReceiptJson)) {
      assertThat(json)
          .doesNotContain(rawReceiptKey)
          .doesNotContain("invoice_file_key")
          .doesNotContain("invoiceFileKey")
          .doesNotContain("/uploads/")
          .doesNotContain("secret-receipt-local");
    }

    // 6. Stream receipt GET & HEAD -> 200 with inline Content-Disposition
    String expectedFilename = "expense-" + expenseId + "-receipt.png";
    String expectedDisposition = "inline; filename=\"" + expectedFilename + "\"";

    mockMvc
        .perform(
            get("/api/v1/admin/expenses/{id}/receipt/content", expenseId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
        .andExpect(
            header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(VALID_PNG_BYTES.length)))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
        .andExpect(content().bytes(VALID_PNG_BYTES));

    mockMvc
        .perform(
            head("/api/v1/admin/expenses/{id}/receipt/content", expenseId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
        .andExpect(
            header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(VALID_PNG_BYTES.length)))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
        .andExpect(content().bytes(new byte[0]));

    // 7. Delete receipt -> 200, S3 object deleted, subsequent stream -> 404
    mockMvc
        .perform(
            delete("/api/v1/admin/expenses/{id}/receipt", expenseId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.receipt").value(Matchers.nullValue()));

    assertThat(privateObjectStorage.exists(rawReceiptKey)).isFalse();

    mockMvc
        .perform(
            get("/api/v1/admin/expenses/{id}/receipt/content", expenseId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_404_RECEIPT"));

    // 8. Delete expense -> 200, subsequent detail -> 404
    mockMvc
        .perform(
            delete("/api/v1/admin/expenses/{id}", expenseId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/admin/expenses/{id}", expenseId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_404"));
  }

  @Test
  @DisplayName(
      "Validation & privacy hygiene: enforces 409 salary conflict, 400 validations, and redacts raw S3 keys")
  void validationAndPrivacyHygiene_enforcesDomainErrorCodesAndRedactsStorageKeys()
      throws Exception {
    String managerToken = tokenFor(managerUser);

    // 1. First SALARY in 2026-10 succeeds; duplicate SALARY in 2026-10 -> 409
    String salaryCreateJson =
        mockMvc
            .perform(
                post("/api/v1/admin/expenses")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "expenseType": "SALARY",
                          "title": "Luong nhan vien thang 10",
                          "amount": 15000000,
                          "expenseDate": "2026-10-05"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID salaryId =
        UUID.fromString(jsonMapper.readTree(salaryCreateJson).path("data").path("id").asText());

    mockMvc
        .perform(
            post("/api/v1/admin/expenses")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "SALARY",
                      "title": "Luong bo sung thang 10",
                      "amount": 2000000,
                      "expenseDate": "2026-10-28"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_409_SALARY_MONTH"));

    // 2. Invalid amounts -> 400 EXPENSE_400_AMOUNT
    mockMvc
        .perform(
            post("/api/v1/admin/expenses")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "OTHER",
                      "title": "Chi phi 0 dong",
                      "amount": 0,
                      "expenseDate": "2026-10-10"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_AMOUNT"));

    mockMvc
        .perform(
            put("/api/v1/admin/expenses/{id}", salaryId)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "SALARY",
                      "title": "Luong le thap phan",
                      "amount": 100000.50,
                      "expenseDate": "2026-10-05"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_AMOUNT"));

    // 3. Missing/empty file on receipt upload -> 400 EXPENSE_400_RECEIPT_REQUIRED
    mockMvc
        .perform(
            multipart("/api/v1/admin/expenses/{id}/receipt", salaryId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_RECEIPT_REQUIRED"));

    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0]);
    mockMvc
        .perform(
            multipart("/api/v1/admin/expenses/{id}/receipt", salaryId)
                .file(emptyFile)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_RECEIPT_REQUIRED"));

    // 4. Invalid/missing export format -> 400 EXPENSE_400_EXPORT_FORMAT
    mockMvc
        .perform(
            get("/api/v1/admin/financial-reports/net-profit/export")
                .param("period", "2026-10")
                .param("format", "CSV")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_EXPORT_FORMAT"));

    mockMvc
        .perform(
            get("/api/v1/admin/financial-reports/net-profit/export")
                .param("period", "2026-10")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_EXPORT_FORMAT"));

    // 5. Upload PDF receipt and confirm neither expense endpoints nor financial report leak raw
    // keys
    MockMultipartFile pdfReceipt =
        new MockMultipartFile(
            "file",
            "confidential-salary-slip.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            VALID_PDF_BYTES);
    String uploadJson =
        mockMvc
            .perform(
                multipart("/api/v1/admin/expenses/{id}/receipt", salaryId)
                    .file(pdfReceipt)
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String rawKey = facilityExpenseRepository.findById(salaryId).orElseThrow().getInvoiceFileKey();
    assertThat(rawKey).isNotBlank();

    String listJson =
        mockMvc
            .perform(
                get("/api/v1/admin/expenses")
                    .param("period", "2026-10")
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String detailJson =
        mockMvc
            .perform(
                get("/api/v1/admin/expenses/{id}", salaryId)
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String reportJson =
        mockMvc
            .perform(
                get("/api/v1/admin/financial-reports/net-profit")
                    .param("period", "2026-10")
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (String json : List.of(uploadJson, listJson, detailJson, reportJson)) {
      assertThat(json)
          .doesNotContain(rawKey)
          .doesNotContain("invoice_file_key")
          .doesNotContain("invoiceFileKey")
          .doesNotContain("/uploads/")
          .doesNotContain("confidential-salary-slip");
    }
  }

  @Test
  @DisplayName(
      "Financial report & exports: returns exact KPIs, mandatory disclaimer, POI-readable XLSX, and valid PDF")
  void financialReportAndExports_returnExactKpisDisclaimerPoiReadableXlsxAndValidPdf()
      throws Exception {
    String managerToken = tokenFor(managerUser);

    User secondTenantUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("fin_sec_tenant2@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant secondTenant =
        tenantRepository.saveAndFlush(
            Tenant.builder()
                .user(secondTenantUser)
                .tenantCode("NT-FIN-SEC-02")
                .fullName("Tran Resident Two")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079205970002")
                .phone("0911970002")
                .permanentAddress("Quan 1, TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    Contract c1 =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode("HD-2026-9701")
                .tenant(tenant)
                .bed(beds.get(0))
                .roomCodeSnapshot(room.getRoomNumber())
                .bedCodeSnapshot(beds.get(0).getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    Contract c2 =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode("HD-2026-9702")
                .tenant(secondTenant)
                .bed(beds.get(1))
                .roomCodeSnapshot(room.getRoomNumber())
                .bedCodeSnapshot(beds.get(1).getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    Invoice paidInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-HD-2026-9701")
                .contract(c1)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.PAID)
                .build());
    paymentTransactionRepository.saveAndFlush(
        PaymentTransaction.builder()
            .invoice(paidInvoice)
            .receiptCode("PT-INV-202610-HD-2026-9701")
            .amount(new BigDecimal("1500000"))
            .paymentMethod(PaymentMethod.CASH)
            .confirmedByStaff(managerStaff)
            .build());

    invoiceRepository.saveAndFlush(
        Invoice.builder()
            .invoiceCode("INV-202610-HD-2026-9702")
            .contract(c2)
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 5))
            .status(InvoiceStatus.DEBT)
            .build());

    MaintenanceTicket ticket =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-FIN-SEC-01")
                .room(room)
                .reporterTenant(tenant)
                .assigneeStaff(techStaff)
                .title("Thay bong den phong P.970")
                .description("Bong den hong")
                .category(TicketCategory.ELECTRIC)
                .priority(TicketPriority.NORMAL)
                .status(TicketStatus.COMPLETED)
                .resolutionNotes("Da thay bong den moi")
                .resolutionImageFileKey("tickets/MT-FIN-SEC/res.jpg")
                .resolvedAt(Instant.parse("2026-10-15T05:00:00Z"))
                .build());
    ticketReplacementPartRepository.saveAndFlush(
        TicketReplacementPart.builder()
            .ticket(ticket)
            .partName("Bong den LED")
            .quantity(2)
            .unitCost(new BigDecimal("100000"))
            .build());

    mockMvc
        .perform(
            post("/api/v1/admin/expenses")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "ELECTRICITY",
                      "title": "Tien dien EVN thang 10",
                      "amount": 500000,
                      "expenseDate": "2026-10-12",
                      "invoiceCode": "EVN-2026-10"
                    }
                    """))
        .andExpect(status().isCreated());

    // 1. Explicit period report JSON
    String reportJson =
        mockMvc
            .perform(
                get("/api/v1/admin/financial-reports/net-profit")
                    .param("period", "2026-10")
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.httpStatus").value(200))
            .andExpect(jsonPath("$.data.period").value("2026-10"))
            .andExpect(
                jsonPath("$.data.label")
                    .value(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL))
            .andExpect(
                jsonPath("$.data.disclaimer")
                    .value(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER))
            .andExpect(jsonPath("$.data.expectedRentRevenue").value(3000000))
            .andExpect(jsonPath("$.data.collectedRentRevenue").value(1500000))
            .andExpect(jsonPath("$.data.maintenancePartsCost").value(200000))
            .andExpect(jsonPath("$.data.facilityExpenseTotal").value(500000))
            .andExpect(jsonPath("$.data.totalExpenses").value(700000))
            .andExpect(jsonPath("$.data.estimatedNetProfit").value(800000))
            .andExpect(jsonPath("$.data.totalInvoiceCount").value(2))
            .andExpect(jsonPath("$.data.paidInvoiceCount").value(1))
            .andExpect(jsonPath("$.data.completedTicketCount").value(1))
            .andExpect(jsonPath("$.data.facilityExpenseCount").value(1))
            .andExpect(jsonPath("$.data.expenseBreakdownByType.ELECTRICITY").value(500000))
            .andExpect(jsonPath("$.data.expenseBreakdownByType.WATER").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode reportData = jsonMapper.readTree(reportJson).path("data");
    assertThat(reportData.path("paidInvoices").size()).isEqualTo(1);
    assertThat(reportData.path("completedMaintenanceTickets").size()).isEqualTo(1);
    assertThat(reportData.path("facilityExpenses").size()).isEqualTo(1);
    assertThat(reportJson)
        .doesNotContain("tickets/MT-FIN-SEC/res.jpg")
        .doesNotContain("invoiceFileKey")
        .doesNotContain("/uploads/");

    // 2. Default period resolves via MutableTestClock to 2026-10
    mockMvc
        .perform(
            get("/api/v1/admin/financial-reports/net-profit")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.period").value("2026-10"))
        .andExpect(jsonPath("$.data.estimatedNetProfit").value(800000));

    // 3. XLSX export -> valid POI workbook
    String expectedXlsxDisposition =
        ContentDisposition.attachment()
            .filename("thu-chi-2026-10.xlsx", StandardCharsets.UTF_8)
            .build()
            .toString();

    byte[] xlsxBytes =
        mockMvc
            .perform(
                get("/api/v1/admin/financial-reports/net-profit/export")
                    .param("period", "2026-10")
                    .param("format", "XLSX")
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_TYPE,
                        FinancialReportExportServiceImpl.XLSX_CONTENT_TYPE))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedXlsxDisposition))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
      assertThat(workbook.getSheetName(0)).isEqualTo("Tong hop thu chi");
      assertThat(workbook.getSheetName(1)).isEqualTo("Chi tiet chi phi");
      assertThat(workbook.getSheetName(2)).isEqualTo("Hoa don da thu");
      assertThat(workbook.getSheetName(3)).isEqualTo("Sua chua hoan thanh");

      Sheet summary = workbook.getSheet("Tong hop thu chi");
      assertNumericCellEquals(summary, 6, 1, 3_000_000d);
      assertNumericCellEquals(summary, 7, 1, 1_500_000d);
      assertNumericCellEquals(summary, 8, 1, 200_000d);
      assertNumericCellEquals(summary, 9, 1, 500_000d);
      assertNumericCellEquals(summary, 10, 1, 700_000d);
      assertNumericCellEquals(summary, 11, 1, 800_000d);
      assertThat(summary.getRow(21).getCell(1).getStringCellValue())
          .isEqualTo(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER);
    }

    // 4. PDF export -> valid %PDF- bytes & readable Vietnamese text
    String expectedPdfDisposition =
        ContentDisposition.attachment()
            .filename("thu-chi-2026-10.pdf", StandardCharsets.UTF_8)
            .build()
            .toString();

    byte[] pdfBytes =
        mockMvc
            .perform(
                get("/api/v1/admin/financial-reports/net-profit/export")
                    .param("period", "2026-10")
                    .param("format", "PDF")
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_TYPE,
                        FinancialReportExportServiceImpl.PDF_CONTENT_TYPE))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedPdfDisposition))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(pdfBytes.length).isGreaterThan(100);
    assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

    try (PdfReader reader = new PdfReader(pdfBytes)) {
      assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      StringBuilder rawText = new StringBuilder();
      for (int page = 1; page <= reader.getNumberOfPages(); page++) {
        rawText.append(extractor.getTextFromPage(page)).append('\n');
      }
      String normalizedText =
          Normalizer.normalize(rawText.toString(), Normalizer.Form.NFC)
              .replaceAll("\\s+", " ")
              .trim();
      assertThat(normalizedText).contains("2026-10");
      assertThat(normalizedText).contains(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL);
      assertThat(normalizedText)
          .contains(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER);
    }
  }

  private void performAll10EndpointsAndHead(
      String bearerToken, ResultMatcher expectedStatus, UUID targetId) throws Exception {
    MockMultipartFile png =
        new MockMultipartFile("file", "receipt.png", MediaType.IMAGE_PNG_VALUE, VALID_PNG_BYTES);

    mockMvc.perform(withAuth(get("/api/v1/admin/expenses"), bearerToken)).andExpect(expectedStatus);
    mockMvc
        .perform(withAuth(get("/api/v1/admin/expenses/{id}", targetId), bearerToken))
        .andExpect(expectedStatus);
    mockMvc
        .perform(
            withAuth(
                post("/api/v1/admin/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_EXPENSE_JSON),
                bearerToken))
        .andExpect(expectedStatus);
    mockMvc
        .perform(
            withAuth(
                put("/api/v1/admin/expenses/{id}", targetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_EXPENSE_JSON),
                bearerToken))
        .andExpect(expectedStatus);
    mockMvc
        .perform(withAuth(delete("/api/v1/admin/expenses/{id}", targetId), bearerToken))
        .andExpect(expectedStatus);
    MockMultipartHttpServletRequestBuilder uploadRequest =
        multipart("/api/v1/admin/expenses/{id}/receipt", targetId).file(png);
    if (bearerToken != null) {
      uploadRequest.header("Authorization", "Bearer " + bearerToken);
    }
    mockMvc.perform(uploadRequest).andExpect(expectedStatus);
    mockMvc
        .perform(withAuth(delete("/api/v1/admin/expenses/{id}/receipt", targetId), bearerToken))
        .andExpect(expectedStatus);
    mockMvc
        .perform(
            withAuth(get("/api/v1/admin/expenses/{id}/receipt/content", targetId), bearerToken))
        .andExpect(expectedStatus);
    mockMvc
        .perform(
            withAuth(head("/api/v1/admin/expenses/{id}/receipt/content", targetId), bearerToken))
        .andExpect(expectedStatus);
    mockMvc
        .perform(withAuth(get("/api/v1/admin/financial-reports/net-profit"), bearerToken))
        .andExpect(expectedStatus);
    mockMvc
        .perform(
            withAuth(
                get("/api/v1/admin/financial-reports/net-profit/export")
                    .param("period", "2026-10")
                    .param("format", "XLSX"),
                bearerToken))
        .andExpect(expectedStatus);
  }

  private static MockHttpServletRequestBuilder withAuth(
      MockHttpServletRequestBuilder builder, String bearerToken) {
    if (bearerToken != null) {
      builder.header("Authorization", "Bearer " + bearerToken);
    }
    return builder;
  }

  private static void assertNumericCellEquals(
      Sheet sheet, int rowIndex, int colIndex, double expected) {
    Cell cell = sheet.getRow(rowIndex).getCell(colIndex);
    assertThat(cell).isNotNull();
    assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
    assertThat(cell.getNumericCellValue()).isEqualTo(expected);
  }
}
