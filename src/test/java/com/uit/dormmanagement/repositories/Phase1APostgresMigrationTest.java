package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateRoomRequest;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.services.RoomService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class Phase1APostgresMigrationTest {

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
  @Autowired private RoomService roomService;

  @Test
  @DisplayName("Fresh PostgreSQL Flyway baseline creates all eight Phase 1A tables")
  void freshMigrationCreatesPhase1aSchema() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "select table_name from information_schema.tables "
                + "where table_schema = current_schema()",
            String.class);

    assertThat(tables)
        .contains(
            "users",
            "tenants",
            "staff_profiles",
            "rooms",
            "beds",
            "amenities",
            "room_amenities",
            "room_code_registry");
    assertThat(columnExists("users", "username")).isFalse();
    assertThat(columnExists("users", "phone")).isFalse();
    assertThat(columnExists("tenants", "email")).isFalse();
    assertThat(columnExists("staff_profiles", "active")).isFalse();
    assertThat(columnExists("rooms", "occupied_beds")).isFalse();
    assertThat(columnExists("beds", "price")).isFalse();
    assertThat(columnExists("amenities", "category")).isFalse();
    Map<String, Set<String>> expectedColumns =
        Map.of(
            "users",
            Set.of("id", "email", "password_hash", "role", "status", "created_at", "updated_at"),
            "tenants",
            Set.of(
                "id", "user_id", "tenant_code", "id_card_number", "full_name", "gender",
                "date_of_birth", "phone", "permanent_address", "emergency_contact_name",
                "emergency_contact_phone", "resident_status", "created_at", "updated_at"),
            "staff_profiles",
            Set.of("id", "user_id", "full_name", "phone", "created_at", "updated_at"),
            "rooms",
            Set.of(
                "id", "room_number", "floor", "total_beds", "gender_policy", "base_price",
                "archived_at", "created_at", "updated_at"),
            "beds",
            Set.of("id", "room_id", "bed_code", "slot_key", "status", "created_at"),
            "amenities",
            Set.of("id", "code", "name"),
            "room_amenities",
            Set.of("room_id", "amenity_id", "quantity"),
            "room_code_registry",
            Set.of("code", "room_id", "is_current", "used_in_business", "created_at"));
    expectedColumns.forEach(
        (table, columns) -> assertThat(tableColumns(table)).containsAll(columns));
    assertThat(jdbcTemplate.queryForList("select code from amenities", String.class))
        .contains("AC", "FRIDGE", "WATER_HEATER", "DESK", "WARDROBE", "CURTAIN", "WIFI_ROUTER");
  }

  @Test
  @DisplayName("PostgreSQL enforces Phase 1A account and room value domains")
  void postgresChecksRejectValuesOutsidePhase1aDomains() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into users(email, password_hash, role, status) values (?, ?, ?, ?)",
                    "bad-status@example.test",
                    "hash",
                    "TENANT",
                    "INACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into users(email, password_hash, role) values (?, ?, ?)",
                    "bad-role@example.test",
                    "hash",
                    "ADMIN"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into tenants(user_id, tenant_code, id_card_number, full_name, gender, "
                        + "phone, permanent_address) values (gen_random_uuid(), ?, ?, ?, ?, ?, ?)",
                    "NT-BAD",
                    "1234",
                    "Invalid CCCD",
                    "MALE",
                    "0900000000",
                    "Test"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into rooms(room_number, floor, total_beds, gender_policy, base_price) "
                        + "values (?, ?, ?, ?, ?)",
                    "P.999",
                    1,
                    5,
                    "MALE",
                    1000000))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertRoomInsertRejected("TC-FLOOR", 0, 4, "MALE", 1_000_000);
    assertRoomInsertRejected("TC-GENDER", 1, 4, "MIXED", 1_000_000);
    assertRoomInsertRejected("TC-PRICE", 1, 4, "MALE", 0);
  }

  @Test
  @DisplayName("Tenant and Staff identity FKs are required and unique; Tenant phones may repeat")
  void profileIdentityConstraintsAndContactPhoneRules() {
    UUID tenantUser1 = insertUser("TENANT");
    UUID tenantUser2 = insertUser("TENANT");
    UUID staffUser = insertUser("TECHNICIAN");
    String cccd1 = randomCccd();
    String cccd2 = randomCccd();
    insertTenant(tenantUser1, "NT-" + UUID.randomUUID(), cccd1, "0900000000");
    insertTenant(tenantUser2, "NT-" + UUID.randomUUID(), cccd2, "0900000000");

    assertThatThrownBy(
            () -> insertTenant(tenantUser1, "NT-DUP-USER", randomCccd(), "0900000001"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> insertTenant(tenantUser2, "NT-DUP-CCCD", cccd1, "0900000002"))
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbcTemplate.update(
        "insert into staff_profiles(user_id, full_name, phone) values (?, ?, ?)",
        staffUser,
        "Technician",
        "0900000000");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into staff_profiles(user_id, full_name, phone) values (?, ?, ?)",
                    staffUser,
                    "Duplicate Technician",
                    "0900000001"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into staff_profiles(user_id, full_name, phone) values (?, ?, ?)",
                    tenantUser1,
                    "Wrong Profile Role",
                    "0900000002"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("PostgreSQL preserves canonical email uniqueness and profile-role consistency")
  void postgresProtectsUserEmailAndStaffRoleInvariants() {
    String email = "unique-" + UUID.randomUUID() + "@example.test";
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into users(id, email, password_hash, role) values (?, ?, ?, 'TECHNICIAN')",
        userId,
        email,
        "test-hash");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into users(email, password_hash, role) values (?, ?, 'MANAGER')",
                    email,
                    "test-hash"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into users(email, password_hash, role) values (?, ?, 'MANAGER')",
                    email.toUpperCase(Locale.ROOT),
                    "test-hash"))
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbcTemplate.update(
        "insert into staff_profiles(user_id, full_name, phone) values (?, ?, ?)",
        userId,
        "Test Technician",
        "0900000000");
    assertThatThrownBy(
            () -> jdbcTemplate.update("update users set role = 'TENANT' where id = ?", userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Room creation persists 4, 6, or 8 Beds and one current room code")
  void roomCreationPersistsConfiguredBedCountAndRegistryCode() {
    for (RoomType roomType : List.of(RoomType.FOUR_BEDS, RoomType.SIX_BEDS, RoomType.EIGHT_BEDS)) {
      String roomNumber =
          ("TC-" + roomType.getBedCount() + "-" + UUID.randomUUID()).toUpperCase(Locale.ROOT);
      roomService.createRoom(
          new CreateRoomRequest(
              roomNumber,
              1,
              roomType,
              GenderPolicy.MALE,
              java.math.BigDecimal.valueOf(1_500_000),
              List.of()));

      Integer bedCount =
          jdbcTemplate.queryForObject(
              "select count(*) from beds b join rooms r on r.id = b.room_id "
                  + "where r.room_number = ?",
              Integer.class,
              roomNumber);
      Integer currentCodeCount =
          jdbcTemplate.queryForObject(
              "select count(*) from room_code_registry where code = ? and is_current",
              Integer.class,
              roomNumber);
      assertThat(bedCount).isEqualTo(roomType.getBedCount());
      assertThat(currentCodeCount).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("Bed code, canonical slot key, and Bed status constraints reject duplicates and legacy values")
  void bedUniquenessAndStatusConstraints() {
    var room =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.408",
                4,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                java.math.BigDecimal.valueOf(1_500_000),
                List.of()));

    assertThatThrownBy(
            () -> insertBed(room.id(), "GX", "P.408-G01", "AVAILABLE"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () -> insertBed(room.id(), "G01", "P.408-G01", "AVAILABLE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> insertBed(room.id(), "GX2", "P.408-GX2", "RESERVED"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("Invalid Room Amenity reference rolls back Room, Beds, and registry writes")
  void invalidAmenityRollsBackRoomCreation() {
    String roomNumber = ("TC-ROLLBACK-" + UUID.randomUUID()).toUpperCase(Locale.ROOT);
    CreateRoomRequest request =
        new CreateRoomRequest(
            roomNumber,
            1,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            java.math.BigDecimal.valueOf(1_500_000),
            List.of(
                new com.uit.dormmanagement.dtos.request.room.RoomAmenityRequest(
                    UUID.randomUUID(), 1)));

    assertThatThrownBy(() -> roomService.createRoom(request))
        .isInstanceOf(com.uit.dormmanagement.exceptions.BaseAppException.class);
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from rooms where room_number = ?", Integer.class, roomNumber))
        .isZero();
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_code_registry where code = ?", Integer.class, roomNumber))
        .isZero();
  }

  @Test
  @DisplayName("Amenity quantity and safe deletion rules are enforced by PostgreSQL")
  void amenityQuantityMustBePositiveAndUsedAmenityCannotBeDeleted() {
    String roomNumber = "P.405";
    var room =
        roomService.createRoom(
            new CreateRoomRequest(
                roomNumber,
                4,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                java.math.BigDecimal.valueOf(1_500_000),
                List.of()));
    UUID amenityId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into amenities(id, code, name) values (?, ?, ?)",
        amenityId,
        "PG-" + UUID.randomUUID(),
        "PostgreSQL test amenity");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into room_amenities(room_id, amenity_id, quantity) values (?, ?, 0)",
                    room.id(),
                    amenityId))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbcTemplate.update(
        "insert into room_amenities(room_id, amenity_id, quantity) values (?, ?, 1)",
        room.id(),
        amenityId);
    assertThatThrownBy(() -> jdbcTemplate.update("delete from amenities where id = ?", amenityId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Room rename retains historical codes and updates all Bed slot keys atomically")
  void roomRenameRetainsHistoricalCodeAndUpdatesSlots() {
    String oldCode = "P.401";
    String newCode = "P.402";
    var created =
        roomService.createRoom(
            new CreateRoomRequest(
                oldCode,
                4,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                java.math.BigDecimal.valueOf(1_500_000),
                List.of()));
    jdbcTemplate.update(
        "update room_code_registry set used_in_business = true where code = ?", oldCode);

    roomService.updateRoom(
        created.id(),
        new UpdateRoomRequest(
            newCode, GenderPolicy.MALE, java.math.BigDecimal.valueOf(1_500_000), null));

    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_code_registry where code = ? and not is_current "
                + "and used_in_business",
            Integer.class,
            oldCode))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_code_registry where code = ? and is_current",
            Integer.class,
            newCode))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from beds where room_id = ? and slot_key like ?",
            Integer.class,
            created.id(),
            newCode + "-%"))
        .isEqualTo(4);
  }

  @Test
  @DisplayName("Archiving retains the current registry code")
  void archiveRetainsCurrentRoomCode() {
    String roomNumber = "P.403";
    var created =
        roomService.createRoom(
            new CreateRoomRequest(
                roomNumber,
                4,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                java.math.BigDecimal.valueOf(1_500_000),
                List.of()));

    roomService.deleteRoom(created.id());

    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from rooms where id = ? and archived_at is not null",
            Integer.class,
            created.id()))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_code_registry where code = ? and is_current",
            Integer.class,
            roomNumber))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Room code business-use history cannot be cleared")
  void registryBusinessHistoryIsMonotonic() {
    String roomNumber = "P.404";
    roomService.createRoom(
        new CreateRoomRequest(
            roomNumber,
            4,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            java.math.BigDecimal.valueOf(1_500_000),
            List.of()));
    jdbcTemplate.update(
        "update room_code_registry set used_in_business = true where code = ?", roomNumber);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update room_code_registry set used_in_business = false where code = ?",
                    roomNumber))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbcTemplate.update("delete from room_code_registry where code = ?", roomNumber))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Creating a business contract automatically protects its Room code")
  void businessReferenceMarksRoomCodeAsUsed() {
    String roomNumber = "P.409";
    var room =
        roomService.createRoom(
            new CreateRoomRequest(
                roomNumber,
                4,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                java.math.BigDecimal.valueOf(1_500_000),
                List.of()));
    UUID tenantUserId = insertUser("TENANT");
    insertTenant(tenantUserId, "NT-" + UUID.randomUUID(), randomCccd(), "0900000000");
    UUID tenantId =
        jdbcTemplate.queryForObject(
            "select id from tenants where user_id = ?", UUID.class, tenantUserId);
    UUID bedId =
        jdbcTemplate.queryForObject(
            "select id from beds where room_id = ? order by bed_code limit 1", UUID.class, room.id());

    jdbcTemplate.update(
        "insert into contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, "
            + "start_on, original_end_on, original_monthly_rent, deposit_amount, status) values (?, ?, ?, ?, ?, "
            + "date_trunc('month', current_date)::date, (date_trunc('month', current_date) + interval '1 month - 1 day')::date, 1500000, 0, 'ACTIVE')",
        "HD-" + UUID.randomUUID(),
        tenantId,
        bedId,
        roomNumber,
        "G01");

    assertThat(jdbcTemplate.queryForObject(
            "select used_in_business from room_code_registry where code = ?", Boolean.class, roomNumber))
        .isTrue();
  }

  @Test
  @DisplayName("Deleting an unused incorrect Room releases its code for valid reuse")
  void deletingUnusedRoomReleasesCode() {
    String roomNumber = "P.406";
    var original =
        roomService.createRoom(
            new CreateRoomRequest(
                roomNumber,
                4,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                java.math.BigDecimal.valueOf(1_500_000),
                List.of()));
    roomService.deleteIncorrectRoom(original.id());

    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from rooms where id = ?", Integer.class, original.id()))
        .isZero();
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_code_registry where code = ?", Integer.class, roomNumber))
        .isZero();
    assertThat(roomService.createRoom(
            new CreateRoomRequest(
                roomNumber,
                4,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                java.math.BigDecimal.valueOf(1_500_000),
                List.of()))
        .roomNumber())
        .isEqualTo(roomNumber);
  }

  @Test
  @DisplayName("Concurrent requests for one Room code create only one Room and its Beds")
  void concurrentDuplicateRoomCodeCreationIsSafe() throws Exception {
    String roomNumber = "P.407";
    CreateRoomRequest request =
        new CreateRoomRequest(
            roomNumber,
            4,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            java.math.BigDecimal.valueOf(1_500_000),
            List.of());
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return createRoomIfAvailable(request);
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return createRoomIfAvailable(request);
              });
      start.countDown();

      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
    }
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from rooms where room_number = ?", Integer.class, roomNumber))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from beds b join rooms r on r.id = b.room_id "
                + "where r.room_number = ?",
            Integer.class,
            roomNumber))
        .isEqualTo(4);
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from room_code_registry where code = ? and is_current",
            Integer.class,
            roomNumber))
        .isEqualTo(1);
  }

  private boolean createRoomIfAvailable(CreateRoomRequest request) {
    try {
      roomService.createRoom(request);
      return true;
    } catch (com.uit.dormmanagement.exceptions.BaseAppException
        | DataIntegrityViolationException expectedConflict) {
      return false;
    }
  }

  private UUID insertUser(String role) {
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into users(id, email, password_hash, role) values (?, ?, ?, ?)",
        userId,
        "profile-" + UUID.randomUUID() + "@example.test",
        "test-hash",
        role);
    return userId;
  }

  private void insertTenant(UUID userId, String tenantCode, String cccd, String phone) {
    jdbcTemplate.update(
        "insert into tenants(user_id, tenant_code, id_card_number, full_name, gender, phone, "
            + "permanent_address) values (?, ?, ?, ?, 'MALE', ?, 'Test address')",
        userId,
        tenantCode,
        cccd,
        "Test Tenant",
        phone);
  }

  private void insertBed(UUID roomId, String bedCode, String slotKey, String status) {
    jdbcTemplate.update(
        "insert into beds(room_id, bed_code, slot_key, status) values (?, ?, ?, ?)",
        roomId,
        bedCode,
        slotKey,
        status);
  }

  private void assertRoomInsertRejected(
      String roomNumber, int floor, int capacity, String gender, int basePrice) {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into rooms(room_number, floor, total_beds, gender_policy, base_price) "
                        + "values (?, ?, ?, ?, ?)",
                    roomNumber,
                    floor,
                    capacity,
                    gender,
                    basePrice))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String randomCccd() {
    return Long.toString(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
  }

  private boolean columnExists(String table, String column) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns "
                + "where table_schema = current_schema() and table_name = ? and column_name = ?",
            Integer.class,
            table,
            column);
    return count != null && count > 0;
  }

  private Set<String> tableColumns(String table) {
    return Set.copyOf(
        jdbcTemplate.queryForList(
            "select column_name from information_schema.columns "
                + "where table_schema = current_schema() and table_name = ?",
            String.class,
            table));
  }
}
