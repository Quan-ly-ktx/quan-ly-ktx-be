package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import com.uit.dormmanagement.exceptions.DataIntegrityViolationClassifier;
import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Transactional
class DataIntegrityClassificationPostgresTest {

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

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSource dataSource;

  private DataIntegrityViolationException captureViolation(Runnable action) {
    Connection conn = DataSourceUtils.getConnection(dataSource);
    Savepoint savepoint;
    try {
      savepoint = conn.setSavepoint();
    } catch (SQLException ex) {
      DataSourceUtils.releaseConnection(conn, dataSource);
      throw new IllegalStateException(ex);
    }
    try {
      DataIntegrityViolationException thrown =
          catchThrowableOfType(DataIntegrityViolationException.class, action::run);
      assertThat(thrown).as("Expected DataIntegrityViolationException").isNotNull();
      return thrown;
    } finally {
      try {
        conn.rollback(savepoint);
      } catch (SQLException ex) {
        throw new IllegalStateException(ex);
      } finally {
        DataSourceUtils.releaseConnection(conn, dataSource);
      }
    }
  }

  @Test
  @DisplayName("Real PostgreSQL constraint violations classify accurately as 409 vs 400")
  void realPostgresConstraints_classify409Vs400() {
    UUID u1 = UUID.randomUUID();
    UUID u2 = UUID.randomUUID();
    UUID t1 = UUID.randomUUID();
    UUID t2 = UUID.randomUUID();
    UUID r1 = UUID.randomUUID();
    UUID r2 = UUID.randomUUID();
    UUID b1 = UUID.randomUUID();
    UUID b2 = UUID.randomUUID();
    UUID v1 = UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO users(id, email, password_hash, role, status) VALUES (?, 'pg_dup@dorm.vn', 'h', 'TENANT', 'ACTIVE')",
        u1);
    jdbcTemplate.update(
        "INSERT INTO users(id, email, password_hash, role, status) VALUES (?, 'pg_other@dorm.vn', 'h', 'TENANT', 'ACTIVE')",
        u2);
    jdbcTemplate.update(
        "INSERT INTO tenants(id, user_id, tenant_code, id_card_number, full_name, gender, phone, permanent_address) "
            + "VALUES (?, ?, 'NT-PG-1', '079203009001', 'Tenant 1', 'MALE', '0901000001', 'Addr 1')",
        t1,
        u1);

    // 1. uq_users_email_canonical -> 409 AUTH_409_EMAIL
    DataIntegrityViolationException dupEmail =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO users(email, password_hash, role, status) VALUES ('pg_dup@dorm.vn', 'h', 'TECHNICIAN', 'ACTIVE')"));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(dupEmail))
        .contains(AuthErrorCode.DUPLICATE_EMAIL);

    // 2. tenants_id_card_number_key -> 409 TENANT_409_ID_CARD
    DataIntegrityViolationException dupCccd =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO tenants(id, user_id, tenant_code, id_card_number, full_name, gender, phone, permanent_address) "
                        + "VALUES (?, ?, 'NT-PG-2', '079203009001', 'Tenant 2', 'MALE', '0901000002', 'Addr 2')",
                    t2,
                    u2));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(dupCccd))
        .contains(TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);

    // 3. uq_rooms_number_canonical + room_code_registry_pkey -> 409 ROOM_409
    jdbcTemplate.update(
        "INSERT INTO rooms(id, room_number, floor, total_beds, gender_policy, base_price) VALUES (?, 'P.701', 7, 4, 'MALE', 1500000)",
        r1);
    jdbcTemplate.update(
        "INSERT INTO room_code_registry(code, room_id, is_current, used_in_business) VALUES ('P.701', ?, true, false)",
        r1);
    for (int i = 1; i <= 4; i++) {
      UUID bid = (i == 1) ? b1 : (i == 2 ? b2 : UUID.randomUUID());
      jdbcTemplate.update(
          "INSERT INTO beds(id, room_id, bed_code, slot_key, status) VALUES (?, ?, ?, ?, 'AVAILABLE')",
          bid,
          r1,
          "G0" + i,
          "P.701-G0" + i);
    }

    DataIntegrityViolationException dupRoom =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO rooms(id, room_number, floor, total_beds, gender_policy, base_price) VALUES (?, 'P.701', 7, 4, 'MALE', 1500000)",
                    r2));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(dupRoom))
        .contains(RoomErrorCode.ROOM_NUMBER_DUPLICATED);

    DataIntegrityViolationException dupRegistry =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO room_code_registry(code, room_id, is_current, used_in_business) VALUES ('P.701', ?, false, true)",
                    r1));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(dupRegistry))
        .contains(RoomErrorCode.ROOM_NUMBER_DUPLICATED);

    // 4. uq_vehicles_issued_card_code, uq_vehicles_tenant_active_quota & change request -> 409
    jdbcTemplate.update(
        "INSERT INTO vehicles(id, tenant_id, vehicle_type, brand, color, card_code, status, issued_at) "
            + "VALUES (?, ?, 'BICYCLE', 'Giant', 'Black', 'PG-CARD-1', 'ISSUED', now())",
        v1,
        t1);
    DataIntegrityViolationException dupQuota =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO vehicles(tenant_id, vehicle_type, brand, color, status) VALUES (?, 'BICYCLE', 'Trek', 'Red', 'PENDING_CARD')",
                    t1));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(dupQuota))
        .contains(VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED);

    jdbcTemplate.update(
        "INSERT INTO tenants(id, user_id, tenant_code, id_card_number, full_name, gender, phone, permanent_address) "
            + "VALUES (?, ?, 'NT-PG-2', '079203009002', 'Tenant 2', 'MALE', '0901000002', 'Addr 2')",
        t2,
        u2);
    DataIntegrityViolationException dupCard =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO vehicles(tenant_id, vehicle_type, brand, color, card_code, status, issued_at) "
                        + "VALUES (?, 'BICYCLE', 'Giant', 'White', 'pg-card-1', 'ISSUED', now())",
                    t2));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(dupCard))
        .contains(VehicleErrorCode.CARD_CODE_DUPLICATED);

    jdbcTemplate.update(
        "INSERT INTO vehicle_change_requests(tenant_id, current_vehicle_id, proposed_vehicle_type, proposed_brand, proposed_color, status) "
            + "VALUES (?, ?, 'BICYCLE', 'Giant', 'Blue', 'PENDING')",
        t1,
        v1);
    DataIntegrityViolationException dupChangeReq =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO vehicle_change_requests(tenant_id, current_vehicle_id, proposed_vehicle_type, proposed_brand, proposed_color, status) "
                        + "VALUES (?, ?, 'BICYCLE', 'Trek', 'Green', 'PENDING')",
                    t1,
                    v1));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(dupChangeReq))
        .contains(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_ALREADY_EXISTS);

    // 5. Mapped active contract constraints (uq_contracts_active_bed / uq_contracts_active_tenant)
    // and unmapped unique violation (contracts_contract_code_key -> WEB_409)
    jdbcTemplate.update(
        "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
            + "VALUES ('HD-PG-1', ?, ?, 'P.701', 'G01', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
        t1,
        b1);
    DataIntegrityViolationException activeBedConflict =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
                        + "VALUES ('HD-PG-2', ?, ?, 'P.701', 'G01', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
                    t2,
                    b1));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(activeBedConflict))
        .contains(ContractErrorCode.BED_ALREADY_RENTED);

    DataIntegrityViolationException activeTenantConflict =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
                        + "VALUES ('HD-PG-3', ?, ?, 'P.701', 'G02', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
                    t1,
                    b2));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(activeTenantConflict))
        .contains(ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT);

    DataIntegrityViolationException unmappedUnique =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
                        + "VALUES ('HD-PG-1', ?, ?, 'P.701', 'G02', '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
                    t2,
                    b2));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(unmappedUnique))
        .contains(WebErrorCode.CONFLICT);

    // 6. CHECK 23514, NOT NULL 23502, FK 23503 -> empty (400 WEB_400 in AppExceptionHandler)
    DataIntegrityViolationException checkErr =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO users(email, password_hash, role, status) VALUES ('badrole@dorm.vn', 'h', 'ADMIN', 'ACTIVE')"));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(checkErr)).isEmpty();

    DataIntegrityViolationException notNullErr =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO users(email, password_hash, role, status) VALUES (NULL, 'h', 'TENANT', 'ACTIVE')"));
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(notNullErr)).isEmpty();

    DataIntegrityViolationException fkErr =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO vehicles(tenant_id, vehicle_type, brand, color, status) VALUES (?, 'BICYCLE', 'Giant', 'Blue', 'PENDING_CARD')",
                    UUID.randomUUID()));
    assertThat(DataIntegrityViolationClassifier.isForeignKeyViolation(fkErr)).isTrue();
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(fkErr)).isEmpty();

    // 7. Data exception 22001 (CCCD longer than varchar(12)) -> empty (400)
    DataIntegrityViolationException dataTrunc =
        captureViolation(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO tenants(user_id, tenant_code, id_card_number, full_name, gender, phone, permanent_address) "
                        + "VALUES (?, 'NT-PG-3', '079203009001999', 'Tenant 3', 'MALE', '0901000003', 'Addr 3')",
                    UUID.randomUUID()));
    AppErrorCode maybeCode =
        DataIntegrityViolationClassifier.classifyGlobal(dataTrunc).orElse(null);
    assertThat(maybeCode).isNull();
  }
}
