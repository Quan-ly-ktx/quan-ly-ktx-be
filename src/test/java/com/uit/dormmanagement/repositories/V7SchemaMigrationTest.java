package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import org.h2.api.ErrorCode;
import org.h2.api.Trigger;
import org.h2.message.DbException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class V7SchemaMigrationTest {

  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private EntityManager entityManager;

  private Tenant tenant1;
  private Tenant tenant2;

  @BeforeEach
  void setUp() {
    // Ensure trigger is registered before inserting
    entityManager
        .createNativeQuery(
            "CREATE TRIGGER IF NOT EXISTS trg_vehicles_v7_partial_unique "
                + "BEFORE INSERT, UPDATE ON vehicles FOR EACH ROW "
                + "CALL \"com.uit.dormmanagement.repositories.V7SchemaMigrationTest$V7VehicleConstraintTrigger\"")
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();

    Room room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.301")
                .floor(3)
                .roomType(RoomType.FOUR_BEDS)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds(4)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    Bed bed1 =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G01")
                .slotKey("P.301-G01")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.OCCUPIED)
                .build());

    Bed bed2 =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G02")
                .slotKey("P.301-G02")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.OCCUPIED)
                .build());

    tenant1 =
        tenantRepository.save(
            Tenant.builder()
                .tenantCode("NT-0001")
                .fullName("Nguyen Van A")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203000001")
                .phone("0987000001")
                .email("nva@gmail.com")
                .permanentAddress("TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    tenant2 =
        tenantRepository.save(
            Tenant.builder()
                .tenantCode("NT-0002")
                .fullName("Tran Van B")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203000002")
                .phone("0987000002")
                .email("tvb@gmail.com")
                .permanentAddress("TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    contractRepository.save(
        Contract.builder()
            .contractCode("HD-2026-0001")
            .tenant(tenant1)
            .bed(bed1)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 9, 1))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .build());

    contractRepository.save(
        Contract.builder()
            .contractCode("HD-2026-0002")
            .tenant(tenant2)
            .bed(bed2)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 9, 1))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .build());
  }

  public static class V7VehicleConstraintTrigger implements Trigger {
    @Override
    public void init(
        Connection conn,
        String schemaName,
        String triggerName,
        String tableName,
        boolean before,
        int type) {}

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
      if (newRow == null) {
        return;
      }

      // Determine column indices from ResultSetMetaData or query
      // vehicles table columns: id, tenant_id, vehicle_type, license_plate, brand, color,
      // card_code, status, registration_front_url, registration_back_url, created_at, deleted_at
      // Let's dynamically find column names or query by row id
      Object id = newRow[0];
      Object licensePlate = null;
      Object cardCode = null;
      Object status = null;
      Object deletedAt = null;

      try (PreparedStatement ps =
          conn.prepareStatement(
              "SELECT COLUMN_NAME, ORDINAL_POSITION FROM INFORMATION_SCHEMA.COLUMNS "
                  + "WHERE TABLE_NAME = 'VEHICLES' ORDER BY ORDINAL_POSITION")) {
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            String colName = rs.getString("COLUMN_NAME").toUpperCase();
            int idx = rs.getInt("ORDINAL_POSITION") - 1;
            if (idx < newRow.length) {
              if ("LICENSE_PLATE".equals(colName)) {
                licensePlate = newRow[idx];
              } else if ("CARD_CODE".equals(colName)) {
                cardCode = newRow[idx];
              } else if ("STATUS".equals(colName)) {
                status = newRow[idx];
              } else if ("DELETED_AT".equals(colName)) {
                deletedAt = newRow[idx];
              } else if ("ID".equals(colName)) {
                id = newRow[idx];
              }
            }
          }
        }
      }

      String statusStr = status != null ? status.toString() : null;
      boolean isDeleted = deletedAt != null;

      // Check 1: Partial unique index on card_code WHERE status = 'ACTIVE' AND deleted_at IS NULL
      if (!isDeleted && "ACTIVE".equals(statusStr) && cardCode != null) {
        String sql =
            "SELECT COUNT(*) FROM vehicles WHERE card_code = ? AND status = 'ACTIVE' "
                + "AND deleted_at IS NULL AND (id != ? OR ? IS NULL)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setObject(1, cardCode);
          ps.setObject(2, id);
          ps.setObject(3, id);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
              throw DbException.get(
                  ErrorCode.DUPLICATE_KEY_1,
                  "Unique index or primary key violation: uq_vehicles_active_card_code");
            }
          }
        }
      }

      // Check 2: Partial unique index on license_plate WHERE status != 'REVOKED' AND deleted_at IS
      // NULL
      if (!isDeleted && !"REVOKED".equals(statusStr) && licensePlate != null) {
        String sql =
            "SELECT COUNT(*) FROM vehicles WHERE license_plate = ? AND status != 'REVOKED' "
                + "AND deleted_at IS NULL AND (id != ? OR ? IS NULL)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setObject(1, licensePlate);
          ps.setObject(2, id);
          ps.setObject(3, id);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
              throw DbException.get(
                  ErrorCode.DUPLICATE_KEY_1,
                  "Unique index or primary key violation: uq_vehicles_active_license_plate");
            }
          }
        }
      }
    }

    @Override
    public void close() {}

    @Override
    public void remove() {}
  }

  @Test
  @DisplayName("cardCode: when revoked, allows reissuing the same cardCode to another vehicle")
  void cardCode_whenRevoked_allowsReissuingToAnotherVehicle() {
    // 1. Insert revoked vehicle with card_code 'THE-001'
    Vehicle v1 =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(tenant1)
                .licensePlate("59-A1 111.11")
                .brand("Honda")
                .color("Black")
                .vehicleType(VehicleType.SCOOTER)
                .cardCode("THE-001")
                .status(VehicleStatus.REVOKED)
                .build());
    entityManager.flush();

    // 2. Insert active vehicle with same card_code 'THE-001' for another tenant
    // -> should succeed because V7 dropped the rigid full-table UNIQUE constraint
    // and replaced it with a partial index only on ACTIVE vehicles
    Vehicle v2 =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(tenant2)
                .licensePlate("59-A2 222.22")
                .brand("Yamaha")
                .color("Red")
                .vehicleType(VehicleType.SCOOTER)
                .cardCode("THE-001")
                .status(VehicleStatus.ACTIVE)
                .build());
    entityManager.flush();
    entityManager.clear();

    Optional<Vehicle> foundRevoked = vehicleRepository.findById(v1.getId());
    Optional<Vehicle> foundActive = vehicleRepository.findById(v2.getId());

    assertThat(foundRevoked).isPresent();
    assertThat(foundActive).isPresent();
    assertThat(foundRevoked.get().getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(foundActive.get().getStatus()).isEqualTo(VehicleStatus.ACTIVE);
    assertThat(foundRevoked.get().getCardCode()).isEqualTo(foundActive.get().getCardCode());
  }

  @Test
  @DisplayName(
      "cardCode: saving two ACTIVE vehicles with the same cardCode throws DataIntegrityViolationException")
  void cardCode_twoActiveVehiclesWithSameCardCode_throwsDataIntegrityViolationException() {
    vehicleRepository.save(
        Vehicle.builder()
            .tenant(tenant1)
            .licensePlate("59-A1 111.11")
            .brand("Honda")
            .color("Black")
            .vehicleType(VehicleType.SCOOTER)
            .cardCode("THE-002")
            .status(VehicleStatus.ACTIVE)
            .build());
    entityManager.flush();

    assertThatThrownBy(
            () ->
                vehicleRepository.saveAndFlush(
                    Vehicle.builder()
                        .tenant(tenant2)
                        .licensePlate("59-A2 222.22")
                        .brand("Yamaha")
                        .color("Red")
                        .vehicleType(VehicleType.SCOOTER)
                        .cardCode("THE-002")
                        .status(VehicleStatus.ACTIVE)
                        .build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "licensePlate: saving two non-REVOKED (ACTIVE and PENDING) vehicles with the same licensePlate throws DataIntegrityViolationException")
  void
      licensePlate_twoNonRevokedVehiclesWithSameLicensePlate_throwsDataIntegrityViolationException() {
    vehicleRepository.save(
        Vehicle.builder()
            .tenant(tenant1)
            .licensePlate("59-B1 999.99")
            .brand("Honda")
            .color("Black")
            .vehicleType(VehicleType.SCOOTER)
            .cardCode("THE-003")
            .status(VehicleStatus.ACTIVE)
            .build());
    entityManager.flush();

    assertThatThrownBy(
            () ->
                vehicleRepository.saveAndFlush(
                    Vehicle.builder()
                        .tenant(tenant2)
                        .licensePlate("59-B1 999.99")
                        .brand("Yamaha")
                        .color("White")
                        .vehicleType(VehicleType.SCOOTER)
                        .cardCode("THE-004")
                        .status(VehicleStatus.PENDING)
                        .build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
