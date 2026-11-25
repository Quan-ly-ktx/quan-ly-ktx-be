package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.entities.*;
import com.uit.dormmanagement.enums.*;
import com.uit.dormmanagement.services.RoomService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class Phase1FullPostgresMigrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
    properties.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    properties.add("spring.flyway.enabled", () -> true);
    properties.add("spring.flyway.baseline-on-migrate", () -> false);
    properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @Autowired private RoomService roomService;

  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractRenewalRepository contractRenewalRepository;
  @Autowired private ContractDocumentRepository contractDocumentRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;
  @Autowired private FacilityExpenseRepository facilityExpenseRepository;
  @Autowired private MaintenanceTicketRepository maintenanceTicketRepository;
  @Autowired private TicketReplacementPartRepository ticketReplacementPartRepository;
  @Autowired private ViolationRepository violationRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private VehicleChangeRequestRepository vehicleChangeRequestRepository;
  @Autowired private NotificationRepository notificationRepository;

  @Test
  @DisplayName("1. Flyway baseline creates exactly 20 application tables with no obsolete columns")
  void exactTwentyTablesAndNoObsoleteColumns() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_name != 'flyway_schema_history'",
            String.class);

    assertThat(tables)
        .hasSize(20)
        .containsExactlyInAnyOrder(
            // 1. Foundation (8)
            "users",
            "tenants",
            "staff_profiles",
            "rooms",
            "beds",
            "amenities",
            "room_amenities",
            "room_code_registry",
            // 2. Contracts & Finance (6)
            "contracts",
            "contract_renewals",
            "contract_documents",
            "invoices",
            "payment_transactions",
            "facility_expenses",
            // 3. Operations & Vehicles (6)
            "maintenance_tickets",
            "ticket_replacement_parts",
            "violations",
            "vehicles",
            "vehicle_change_requests",
            "notifications");

    // Verify all prohibited/obsolete columns are completely absent
    assertThat(columnExists("payment_transactions", "bank_reference")).isFalse();
    assertThat(columnExists("maintenance_tickets", "total_cost")).isFalse();
    assertThat(columnExists("maintenance_tickets", "deadline")).isFalse();
    assertThat(columnExists("contracts", "deleted_at")).isFalse();
    assertThat(columnExists("contracts", "signed_at")).isFalse();
    assertThat(columnExists("vehicles", "deleted_at")).isFalse();
    assertThat(columnExists("vehicles", "registration_front_url")).isFalse();
    assertThat(columnExists("vehicles", "registration_back_url")).isFalse();
    assertThat(columnExists("contract_documents", "file_size")).isFalse();
    assertThat(columnExists("contract_documents", "size_bytes")).isFalse();
    assertThat(columnExists("facility_expenses", "invoice_photo_url")).isFalse();
    assertThat(columnExists("violations", "kind")).isFalse();
    assertThat(columnExists("invoices", "paid_at")).isFalse();
    assertThat(columnExists("rooms", "occupied_beds")).isFalse();
    assertThat(columnExists("beds", "price")).isFalse();
    assertThat(columnExists("amenities", "category")).isFalse();
  }

  @Test
  @DisplayName("2. Contracts enforce active uniqueness per bed/tenant, start day 1, and checkout status")
  void contractsIntegrityRules() {
    UUID tenantId1 = createTenantFixture("NT-C1", "079203000001");
    UUID tenantId2 = createTenantFixture("NT-C2", "079203000002");
    UUID roomId = createRoomFixture("P.801");
    UUID bedId1 = getFirstBedId(roomId);
    UUID bedId2 = getSecondBedId(roomId);

    // 1st active contract succeeds
    jdbcTemplate.update(
        "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
            + "start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
            + "VALUES (?, ?, ?, 'P.801', 'G01', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
        "HD-ACT-1",
        tenantId1,
        bedId1);

    // Duplicate active contract on same bed is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
                        + "start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
                        + "VALUES (?, ?, ?, 'P.801', 'G01', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
                    "HD-DUP-BED",
                    tenantId2,
                    bedId1))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Duplicate active contract for same tenant is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
                        + "start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
                        + "VALUES (?, ?, ?, 'P.801', 'G02', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
                    "HD-DUP-TENANT",
                    tenantId1,
                    bedId2))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Start_on must be 1st day of month
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
                        + "start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
                        + "VALUES (?, ?, ?, 'P.801', 'G02', '2026-09-02', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
                    "HD-BAD-START",
                    tenantId2,
                    bedId2))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Checkout status rule: ACTIVE requires checked_out_at IS NULL
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
                        + "start_on, original_end_on, original_monthly_rent, deposit_amount, status, checked_out_at) "
                        + "VALUES (?, ?, ?, 'P.801', 'G02', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE', now())",
                    "HD-BAD-CHECKOUT",
                    tenantId2,
                    bedId2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("3. Contract renewals enforce sequence uniqueness, effective day 1, and valid ranges")
  void contractRenewalsRules() {
    UUID tenantId = createTenantFixture("NT-R1", "079203000003");
    UUID roomId = createRoomFixture("P.802");
    UUID bedId = getFirstBedId(roomId);

    UUID contractId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO contracts(id, contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
            + "start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
            + "VALUES (?, 'HD-REN-1', ?, ?, 'P.802', 'G01', '2026-01-01', '2026-06-30', 1500000, 1500000, 'ACTIVE')",
        contractId,
        tenantId,
        bedId);

    // Sequence 1 succeeds
    jdbcTemplate.update(
        "INSERT INTO contract_renewals(id, contract_id, sequence_no, signed_on, previous_end_on, "
            + "effective_from, new_end_on, monthly_rent) "
            + "VALUES (gen_random_uuid(), ?, 1, '2026-06-15', '2026-06-30', '2026-07-01', '2026-12-31', 1600000)",
        contractId);

    // Duplicate sequence 1 on same contract is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contract_renewals(id, contract_id, sequence_no, signed_on, previous_end_on, "
                        + "effective_from, new_end_on, monthly_rent) "
                        + "VALUES (gen_random_uuid(), ?, 1, '2026-12-15', '2026-12-31', '2027-01-01', '2027-06-30', 1700000)",
                    contractId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Effective date must be 1st day of month
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contract_renewals(id, contract_id, sequence_no, signed_on, previous_end_on, "
                        + "effective_from, new_end_on, monthly_rent) "
                        + "VALUES (gen_random_uuid(), ?, 2, '2026-12-15', '2026-12-31', '2027-01-02', '2027-06-30', 1700000)",
                    contractId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("4. Contract documents enforce RENEWAL_ANNEX renewal_id requirement and contract_id match")
  void contractDocumentsRules() {
    UUID tenantId = createTenantFixture("NT-D1", "079203000004");
    UUID roomId = createRoomFixture("P.803");
    UUID bedId = getFirstBedId(roomId);

    UUID contractId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO contracts(id, contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
            + "start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
            + "VALUES (?, 'HD-DOC-1', ?, ?, 'P.803', 'G01', '2026-01-01', '2026-06-30', 1500000, 1500000, 'ACTIVE')",
        contractId,
        tenantId,
        bedId);

    UUID renewalId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO contract_renewals(id, contract_id, sequence_no, signed_on, previous_end_on, "
            + "effective_from, new_end_on, monthly_rent) "
            + "VALUES (?, ?, 1, '2026-06-15', '2026-06-30', '2026-07-01', '2026-12-31', 1600000)",
        renewalId,
        contractId);

    // ORIGINAL_CONTRACT with null renewal_id succeeds
    jdbcTemplate.update(
        "INSERT INTO contract_documents(contract_id, renewal_id, document_type, original_filename, mime_type, storage_file_key) "
            + "VALUES (?, NULL, 'ORIGINAL_CONTRACT', 'contract.pdf', 'application/pdf', 'contracts/doc1.pdf')",
        contractId);

    // ORIGINAL_CONTRACT with non-null renewal_id is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contract_documents(contract_id, renewal_id, document_type, original_filename, mime_type, storage_file_key) "
                        + "VALUES (?, ?, 'ORIGINAL_CONTRACT', 'contract.pdf', 'application/pdf', 'contracts/doc-bad.pdf')",
                    contractId,
                    renewalId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // RENEWAL_ANNEX with null renewal_id is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contract_documents(contract_id, renewal_id, document_type, original_filename, mime_type, storage_file_key) "
                        + "VALUES (?, NULL, 'RENEWAL_ANNEX', 'annex.pdf', 'application/pdf', 'contracts/annex-bad.pdf')",
                    contractId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // RENEWAL_ANNEX with valid matching renewal_id succeeds
    jdbcTemplate.update(
        "INSERT INTO contract_documents(contract_id, renewal_id, document_type, original_filename, mime_type, storage_file_key) "
            + "VALUES (?, ?, 'RENEWAL_ANNEX', 'annex.pdf', 'application/pdf', 'contracts/annex-ok.pdf')",
        contractId,
        renewalId);
  }

  @Test
  @DisplayName("5. Invoices enforce day 1 billing period, period uniqueness, and single confirmed receipt")
  void invoicesAndPaymentsRules() {
    UUID tenantId = createTenantFixture("NT-I1", "079203000005");
    UUID roomId = createRoomFixture("P.804");
    UUID bedId = getFirstBedId(roomId);
    UUID staffId = createStaffFixture("0901111111");

    UUID contractId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO contracts(id, contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
            + "start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
            + "VALUES (?, 'HD-INV-1', ?, ?, 'P.804', 'G01', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
        contractId,
        tenantId,
        bedId);

    UUID invoiceId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO invoices(id, invoice_code, contract_id, billing_period, amount, due_on, status) "
            + "VALUES (?, 'INV-2026-09', ?, '2026-09-01', 1500000, '2026-09-05', 'DEBT')",
        invoiceId,
        contractId);

    // Duplicate invoice for same contract and billing period is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO invoices(invoice_code, contract_id, billing_period, amount, due_on, status) "
                        + "VALUES ('INV-2026-09-DUP', ?, '2026-09-01', 1500000, '2026-09-05', 'DEBT')",
                    contractId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Billing period must be day 1
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO invoices(invoice_code, contract_id, billing_period, amount, due_on, status) "
                        + "VALUES ('INV-2026-10-BAD', ?, '2026-10-02', 1500000, '2026-10-05', 'DEBT')",
                    contractId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Confirmed payment transaction succeeds
    jdbcTemplate.update(
        "INSERT INTO payment_transactions(invoice_id, receipt_code, amount, payment_method, confirmed_by_staff_id) "
            + "VALUES (?, 'REC-001', 1500000, 'VIETQR_TRANSFER', ?)",
        invoiceId,
        staffId);

    // Second confirmed receipt for same invoice is rejected (one confirmed receipt per invoice)
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO payment_transactions(invoice_id, receipt_code, amount, payment_method, confirmed_by_staff_id) "
                        + "VALUES (?, 'REC-002', 1500000, 'CASH', ?)",
                    invoiceId,
                    staffId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("6. Facility expenses enforce at most one SALARY expense per month")
  void facilityExpensesSalaryUniqueness() {
    UUID staffId = createStaffFixture("0902222222");

    // 1st SALARY for 2026-09 succeeds
    jdbcTemplate.update(
        "INSERT INTO facility_expenses(recorded_by_staff_id, expense_date, expense_type, title, amount) "
            + "VALUES (?, '2026-09-15', 'SALARY', 'Luong T09/2026', 12000000)",
        staffId);

    // 2nd SALARY in same month is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO facility_expenses(recorded_by_staff_id, expense_date, expense_type, title, amount) "
                        + "VALUES (?, '2026-09-28', 'SALARY', 'Luong thuong T09/2026', 2000000)",
                    staffId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Non-SALARY in same month succeeds
    jdbcTemplate.update(
        "INSERT INTO facility_expenses(recorded_by_staff_id, expense_date, expense_type, title, amount) "
            + "VALUES (?, '2026-09-20', 'ELECTRICITY', 'Tien dien T09/2026', 5000000)",
        staffId);
  }

  @Test
  @DisplayName("7. Vehicles enforce quota (1 active per tenant) and unique issued card code")
  void vehiclesQuotaAndCardCodeRules() {
    UUID tenantId1 = createTenantFixture("NT-V1", "079203000006");
    UUID tenantId2 = createTenantFixture("NT-V2", "079203000007");

    // 1st active vehicle (PENDING_CARD) succeeds
    jdbcTemplate.update(
        "INSERT INTO vehicles(tenant_id, vehicle_type, brand, color, status) "
            + "VALUES (?, 'BICYCLE', 'Martin', 'Blue', 'PENDING_CARD')",
        tenantId1);

    // 2nd active vehicle for same tenant (PENDING_CARD or ISSUED) is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO vehicles(tenant_id, vehicle_type, brand, color, status) "
                        + "VALUES (?, 'BICYCLE', 'Asama', 'Red', 'PENDING_CARD')",
                    tenantId1))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Issued vehicle for tenant 2 with card_code succeeds
    jdbcTemplate.update(
        "INSERT INTO vehicles(tenant_id, vehicle_type, brand, color, card_code, status, issued_at) "
            + "VALUES (?, 'BICYCLE', 'Giant', 'Black', 'CARD-1001', 'ISSUED', now())",
        tenantId2);

    // Duplicate card_code for another ISSUED vehicle is rejected
    UUID tenantId3 = createTenantFixture("NT-V3", "079203000008");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO vehicles(tenant_id, vehicle_type, brand, color, card_code, status, issued_at) "
                        + "VALUES (?, 'BICYCLE', 'Trek', 'White', 'card-1001', 'ISSUED', now())",
                    tenantId3))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("8. Notifications enforce dedupe_key uniqueness when non-null")
  void notificationsDedupeKeyRules() {
    UUID tenantId = createTenantFixture("NT-N1", "079203000009");

    // Notification with dedupe_key succeeds
    jdbcTemplate.update(
        "INSERT INTO notifications(tenant_id, event_type, title, body, dedupe_key) "
            + "VALUES (?, 'INVOICE_ISSUED', 'Hoa don moi', 'Ban co hoa don', 'inv-2026-09-t1')",
        tenantId);

    // Duplicate non-null dedupe_key is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO notifications(tenant_id, event_type, title, body, dedupe_key) "
                        + "VALUES (?, 'INVOICE_DUE', 'Nhac nho', 'Sap den han', 'inv-2026-09-t1')",
                    tenantId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Multiple null dedupe_keys succeed
    jdbcTemplate.update(
        "INSERT INTO notifications(tenant_id, event_type, title, body, dedupe_key) "
            + "VALUES (?, 'VIOLATION_CREATED', 'Bien ban', 'No dung quy dinh', NULL)",
        tenantId);
    jdbcTemplate.update(
        "INSERT INTO notifications(tenant_id, event_type, title, body, dedupe_key) "
            + "VALUES (?, 'VIOLATION_CREATED', 'Bien ban 2', 'No dung quy dinh 2', NULL)",
        tenantId);
  }

  @Test
  @DisplayName("9. Maintenance tickets and replacement parts enforce lifecycle and numeric constraints")
  void maintenanceTicketsAndPartsRules() {
    UUID tenantId = createTenantFixture("NT-M1", "079203000010");
    UUID roomId = createRoomFixture("P.805");
    UUID staffId = createStaffFixture("0903333333");

    // UNASSIGNED ticket requires assignee_staff_id IS NULL
    UUID ticketId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO maintenance_tickets(id, ticket_code, reporter_tenant_id, room_id, title, description, category, priority, status) "
            + "VALUES (?, 'TK-001', ?, ?, 'Hong voi nuoc', 'Voi nuoc bi ro ri', 'WATER', 'NORMAL', 'UNASSIGNED')",
        ticketId,
        tenantId,
        roomId);

    // Replacement part with positive quantity and non-negative unit cost succeeds
    jdbcTemplate.update(
        "INSERT INTO ticket_replacement_parts(ticket_id, part_name, quantity, unit_cost) "
            + "VALUES (?, 'Van nuoc', 1, 45000)",
        ticketId);

    // Replacement part with quantity <= 0 is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO ticket_replacement_parts(ticket_id, part_name, quantity, unit_cost) "
                        + "VALUES (?, 'Van nuoc hong', 0, 45000)",
                    ticketId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("10. Violations enforce type domain, level, and fine_amount constraints")
  void violationsRules() {
    UUID tenantId = createTenantFixture("NT-VI1", "079203000011");
    UUID staffId = createStaffFixture("0904444444");

    // REMINDER requires fine_amount = 0
    jdbcTemplate.update(
        "INSERT INTO violations(violation_code, tenant_id, issued_by_staff_id, occurred_on, violation_type, level, fine_amount) "
            + "VALUES ('VIO-001', ?, ?, '2026-09-10', 'OUTSIDER', 'REMINDER', 0)",
        tenantId,
        staffId);

    // REMINDER with fine_amount > 0 is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO violations(violation_code, tenant_id, issued_by_staff_id, occurred_on, violation_type, level, fine_amount) "
                        + "VALUES ('VIO-BAD', ?, ?, '2026-09-10', 'OUTSIDER', 'REMINDER', 50000)",
                    tenantId,
                    staffId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // FINE requires fine_amount > 0
    jdbcTemplate.update(
        "INSERT INTO violations(violation_code, tenant_id, issued_by_staff_id, occurred_on, violation_type, level, fine_amount) "
            + "VALUES ('VIO-002', ?, ?, '2026-09-11', 'SMOKING_FIRE', 'FINE', 200000)",
        tenantId,
        staffId);

    // FINE with fine_amount = 0 is rejected
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO violations(violation_code, tenant_id, issued_by_staff_id, occurred_on, violation_type, level, fine_amount) "
                        + "VALUES ('VIO-BAD-FINE', ?, ?, '2026-09-11', 'SMOKING_FIRE', 'FINE', 0)",
                    tenantId,
                    staffId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Transactional
  @DisplayName("11. JPA entity CRUD smoke test across newly aligned and created entities")
  void jpaEntitiesCrudSmokeTest() {
    // 1. User & Tenant
    User tenantUser =
        userRepository.save(
            User.builder()
                .email("tenant.jpa@example.test")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant tenant =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-JPA-1")
                .idCardNumber("079203000099")
                .fullName("Nguyen Van JPA")
                .gender(GenderPolicy.MALE)
                .phone("0909999999")
                .permanentAddress("TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    // 2. Staff
    User staffUser =
        userRepository.save(
            User.builder()
                .email("staff.jpa@example.test")
                .passwordHash("hash")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    StaffProfile staff =
        staffProfileRepository.save(
            StaffProfile.builder()
                .user(staffUser)
                .fullName("Tran Van Staff")
                .phone("0908888888")
                .build());

    // 3. Room & Bed
    var roomResp =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.888",
                8,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1800000"),
                List.of()));
    Room room = roomRepository.findById(roomResp.id()).orElseThrow();
    Bed bed = bedRepository.findByRoomId(room.getId()).get(0);

    // 4. Contract
    Contract contract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-JPA-888")
                .tenant(tenant)
                .bed(bed)
                .roomCodeSnapshot("P.888")
                .bedCodeSnapshot("G01")
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1800000"))
                .depositAmount(new BigDecimal("1800000"))
                .status(ContractStatus.ACTIVE)
                .build());

    // 5. ContractRenewal
    ContractRenewal renewal =
        contractRenewalRepository.save(
            ContractRenewal.builder()
                .contract(contract)
                .sequenceNo(1)
                .signedOn(LocalDate.of(2027, 2, 15))
                .previousEndOn(LocalDate.of(2027, 2, 28))
                .effectiveFrom(LocalDate.of(2027, 3, 1))
                .newEndOn(LocalDate.of(2027, 8, 31))
                .monthlyRent(new BigDecimal("1900000"))
                .build());

    // 6. ContractDocument
    ContractDocument doc =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(contract)
                .renewal(renewal)
                .documentType(ContractDocumentCategory.RENEWAL_ANNEX)
                .originalFilename("phu-luc-gia-han.pdf")
                .mimeType("application/pdf")
                .storageFileKey("contracts/phu-luc-1.pdf")
                .build());

    // 7. Invoice & PaymentTransaction
    Invoice invoice =
        invoiceRepository.save(
            Invoice.builder()
                .invoiceCode("INV-JPA-888")
                .contract(contract)
                .billingPeriod(LocalDate.of(2026, 9, 1))
                .amount(new BigDecimal("1800000"))
                .dueOn(LocalDate.of(2026, 9, 5))
                .status(InvoiceStatus.PAID)
                .build());

    PaymentTransaction payment =
        paymentTransactionRepository.save(
            PaymentTransaction.builder()
                .invoice(invoice)
                .receiptCode("REC-JPA-888")
                .amount(new BigDecimal("1800000"))
                .paymentMethod(PaymentMethod.VIETQR_TRANSFER)
                .confirmedByStaff(staff)
                .build());

    // 8. FacilityExpense
    FacilityExpense expense =
        facilityExpenseRepository.save(
            FacilityExpense.builder()
                .recordedByStaff(staff)
                .expenseDate(LocalDate.of(2026, 9, 20))
                .expenseType(ExpenseType.INTERNET)
                .title("Internet cap quang FPT T09")
                .amount(new BigDecimal("1200000"))
                .build());

    // 9. MaintenanceTicket & TicketReplacementPart
    MaintenanceTicket ticket =
        maintenanceTicketRepository.save(
            MaintenanceTicket.builder()
                .ticketCode("TK-JPA-001")
                .reporterTenant(tenant)
                .room(room)
                .bed(bed)
                .assigneeStaff(staff)
                .title("Bong den bi chay")
                .description("Bong den LED phong bi chay can thay")
                .category(TicketCategory.ELECTRIC)
                .priority(TicketPriority.NORMAL)
                .status(TicketStatus.ASSIGNED)
                .build());

    TicketReplacementPart part =
        ticketReplacementPartRepository.save(
            TicketReplacementPart.builder()
                .ticket(ticket)
                .partName("Bong LED Rạng Đông 15W")
                .quantity(1)
                .unitCost(new BigDecimal("65000"))
                .build());

    // 10. Violation
    Violation violation =
        violationRepository.save(
            Violation.builder()
                .violationCode("VIO-JPA-001")
                .tenant(tenant)
                .issuedByStaff(staff)
                .occurredOn(LocalDate.of(2026, 9, 12))
                .violationType(ViolationType.OUTSIDER)
                .level(ViolationLevel.WARNING)
                .fineAmount(BigDecimal.ZERO)
                .note("Dan nguoi la vao phong sau 23h")
                .build());

    // 11. Vehicle & VehicleChangeRequest
    Vehicle vehicle =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(tenant)
                .vehicleType(VehicleType.BICYCLE)
                .brand("Thong Nhat")
                .color("Green")
                .cardCode("CARD-JPA-01")
                .status(VehicleStatus.ISSUED)
                .issuedAt(Instant.now())
                .build());

    VehicleChangeRequest changeReq =
        vehicleChangeRequestRepository.save(
            VehicleChangeRequest.builder()
                .tenant(tenant)
                .currentVehicle(vehicle)
                .proposedVehicleType(VehicleType.BICYCLE)
                .proposedBrand("Giant")
                .proposedColor("Blue")
                .status(VehicleChangeRequestStatus.PENDING)
                .build());

    // 12. Notification
    Notification notif =
        notificationRepository.save(
            Notification.builder()
                .tenant(tenant)
                .eventType(NotificationEventType.INVOICE_ISSUED)
                .title("Thong bao tien phong")
                .body("Tien phong thang 9 da duoc tao")
                .dedupeKey("jpa-notif-t1-sep")
                .build());

    entityManager.flush();
    entityManager.clear();

    // Verify navigability and persistence
    Contract loadedContract = contractRepository.findById(contract.getId()).orElseThrow();
    assertThat(loadedContract.getTenant().getFullName()).isEqualTo("Nguyen Van JPA");
    assertThat(loadedContract.getBed().getRoom().getRoomNumber()).isEqualTo("P.888");

    ContractRenewal loadedRenewal = contractRenewalRepository.findById(renewal.getId()).orElseThrow();
    assertThat(loadedRenewal.getContract().getId()).isEqualTo(contract.getId());

    ContractDocument loadedDoc = contractDocumentRepository.findById(doc.getId()).orElseThrow();
    assertThat(loadedDoc.getRenewal().getId()).isEqualTo(renewal.getId());

    PaymentTransaction loadedPayment = paymentTransactionRepository.findById(payment.getId()).orElseThrow();
    assertThat(loadedPayment.getInvoice().getContract().getId()).isEqualTo(contract.getId());
    assertThat(loadedPayment.getConfirmedByStaff().getFullName()).isEqualTo("Tran Van Staff");

    VehicleChangeRequest loadedReq = vehicleChangeRequestRepository.findById(changeReq.getId()).orElseThrow();
    assertThat(loadedReq.getCurrentVehicle().getId()).isEqualTo(vehicle.getId());
    assertThat(loadedReq.getTenant().getId()).isEqualTo(tenant.getId());

    Notification loadedNotif = notificationRepository.findById(notif.getId()).orElseThrow();
    assertThat(loadedNotif.getDedupeKey()).isEqualTo("jpa-notif-t1-sep");
  }

  @Test
  @DisplayName("12. Negative verification: role domain, status domain, bed ON DELETE RESTRICT, deadline_at metadata")
  void testSpecificNegativeConstraintsAndThreeIssues() {
    // 1. Role domain: ADMIN rejected; MANAGER, TENANT, TECHNICIAN accepted
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO users(email, password_hash, role, status) VALUES ('admin@test.vn', 'hash', 'ADMIN', 'ACTIVE')"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO users(email, password_hash, role, status) VALUES ('staff@test.vn', 'hash', 'STAFF', 'ACTIVE')"))
        .isInstanceOf(DataIntegrityViolationException.class);

    UUID mgrId = UUID.randomUUID();
    UUID tntId = UUID.randomUUID();
    UUID techId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO users(id, email, password_hash, role, status) VALUES (?, 'mgr@test.vn', 'hash', 'MANAGER', 'ACTIVE')", mgrId);
    jdbcTemplate.update("INSERT INTO users(id, email, password_hash, role, status) VALUES (?, 'tnt@test.vn', 'hash', 'TENANT', 'ACTIVE')", tntId);
    jdbcTemplate.update("INSERT INTO users(id, email, password_hash, role, status) VALUES (?, 'tech@test.vn', 'hash', 'TECHNICIAN', 'ACTIVE')", techId);

    // 2. Status domain: INACTIVE rejected; ACTIVE, LOCKED accepted
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO users(email, password_hash, role, status) VALUES ('inactive@test.vn', 'hash', 'TENANT', 'INACTIVE')"))
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbcTemplate.update("UPDATE users SET status = 'LOCKED' WHERE id = ?", tntId);

    // 3. Bed FK deletion: Direct deletion of room does not cascade to beds; rejected by ON DELETE RESTRICT
    UUID roomId = createRoomFixture("P.901");
    assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM rooms WHERE id = ?", roomId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // 4. Maintenance deadline_at metadata: exists, is nullable, type is timestamp with time zone
    java.util.Map<String, Object> colMeta =
        jdbcTemplate.queryForMap(
            "SELECT data_type, is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'maintenance_tickets' AND column_name = 'deadline_at'");
    assertThat(colMeta.get("data_type")).isEqualTo("timestamp with time zone");
    assertThat(colMeta.get("is_nullable")).isEqualTo("YES");
    assertThat(columnExists("maintenance_tickets", "deadline")).isFalse();
  }

  // --- Helpers ---

  private UUID createTenantFixture(String code, String cccd) {
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users(id, email, password_hash, role, status) VALUES (?, ?, 'hash', 'TENANT', 'ACTIVE')",
        userId,
        code.toLowerCase() + "@example.test");
    UUID tenantId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO tenants(id, user_id, tenant_code, id_card_number, full_name, gender, phone, permanent_address, resident_status) "
            + "VALUES (?, ?, ?, ?, 'Tenant Name', 'MALE', '0900000000', 'Address', 'ACTIVE')",
        tenantId,
        userId,
        code,
        cccd);
    return tenantId;
  }

  private UUID createStaffFixture(String phone) {
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users(id, email, password_hash, role, status) VALUES (?, ?, 'hash', 'MANAGER', 'ACTIVE')",
        userId,
        "staff-" + UUID.randomUUID() + "@example.test");
    UUID staffId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO staff_profiles(id, user_id, full_name, phone) VALUES (?, ?, 'Staff Name', ?)",
        staffId,
        userId,
        phone);
    return staffId;
  }

  private UUID createRoomFixture(String roomNumber) {
    var response =
        roomService.createRoom(
            new CreateRoomRequest(
                roomNumber,
                8,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                BigDecimal.valueOf(1500000),
                List.of()));
    return response.id();
  }

  private UUID getFirstBedId(UUID roomId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM beds WHERE room_id = ? AND bed_code = 'G01'", UUID.class, roomId);
  }

  private UUID getSecondBedId(UUID roomId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM beds WHERE room_id = ? AND bed_code = 'G02'", UUID.class, roomId);
  }

  private boolean columnExists(String table, String column) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE lower(table_name) = ? AND lower(column_name) = ?",
            Integer.class,
            table.toLowerCase(),
            column.toLowerCase());
    return count != null && count > 0;
  }
}
