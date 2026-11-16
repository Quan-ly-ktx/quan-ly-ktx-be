package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.entities.Amenity;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.RoomAmenity;
import com.uit.dormmanagement.entities.RoomAmenityId;
import com.uit.dormmanagement.entities.RoomCodeRegistry;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class Phase1AEntityMappingTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private AmenityRepository amenityRepository;
  @Autowired private RoomAmenityRepository roomAmenityRepository;
  @Autowired private RoomCodeRegistryRepository roomCodeRegistryRepository;

  @Test
  @DisplayName("Phase 1A JPA tables omit removed and derived persistence fields")
  void phase1aMappingsOmitRemovedColumns() {
    assertColumnsAbsent("users", "username", "phone");
    assertColumnsAbsent("tenants", "email", "deleted_at");
    assertColumnsAbsent("staff_profiles", "email", "active");
    assertColumnsAbsent("rooms", "room_type", "occupied_beds", "deleted_at");
    assertColumnsAbsent("beds", "price");
    assertColumnsAbsent("amenities", "category");
    assertThat(tableColumns("room_code_registry"))
        .contains("code", "room_id", "is_current", "used_in_business", "created_at");
  }

  @Test
  @DisplayName("Identity profiles and room foundation persist with derived Bed pricing")
  void phase1aRelationshipsPersist() {
    User tenantUser = saveUser(" Tenant.User@Example.Test ", UserRole.TENANT);
    Tenant tenant =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-TEST-1")
                .idCardNumber("079203001187")
                .fullName("Test Tenant")
                .gender(GenderPolicy.MALE)
                .phone("0900000000")
                .permanentAddress("Address")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    User staffUser = saveUser("staff@example.test", UserRole.TECHNICIAN);
    StaffProfile staff =
        staffProfileRepository.save(
            StaffProfile.builder()
                .user(staffUser)
                .fullName("Test Technician")
                .phone("0900000000")
                .build());

    Room room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.302")
                .floor(3)
                .totalBeds((short) 4)
                .genderPolicy(GenderPolicy.MALE)
                .basePrice(new BigDecimal("1500000"))
                .build());
    RoomCodeRegistry roomCode =
        roomCodeRegistryRepository.save(
            RoomCodeRegistry.builder().code("P.302").room(room).current(true).build());

    Bed bed =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G01")
                .slotKey("P.302-G01")
                .status(BedStatus.AVAILABLE)
                .build());
    Amenity amenity =
        amenityRepository.save(Amenity.builder().code("AIR_CONDITIONER").name("Air conditioner").build());
    RoomAmenity roomAmenity =
        roomAmenityRepository.save(
            RoomAmenity.builder()
                .id(new RoomAmenityId(room.getId(), amenity.getId()))
                .room(room)
                .amenity(amenity)
                .quantity(2)
                .build());

    entityManager.flush();
    jdbcTemplate.queryForObject(
        "select email from users where id = ?", String.class, tenantUser.getId());
    assertThat(tenant.getUser().getEmail()).isEqualTo("tenant.user@example.test");
    assertThat(staff.getUser().getRole()).isEqualTo(UserRole.TECHNICIAN);
    assertThat(room.getBasePrice()).isEqualByComparingTo("1500000");
    assertThat(bed.getRoom().getBasePrice()).isEqualByComparingTo("1500000");
    assertThat(roomCode.isCurrent()).isTrue();
    assertThat(roomAmenity.getQuantity()).isEqualTo(2);
  }

  private User saveUser(String email, UserRole role) {
    return userRepository.save(
        User.builder()
            .email(email)
            .passwordHash("test-hash")
            .role(role)
            .status(UserStatus.ACTIVE)
            .build());
  }

  private void assertColumnsAbsent(String table, String... absent) {
    assertThat(tableColumns(table)).doesNotContain(absent);
  }

  private List<String> tableColumns(String table) {
    return jdbcTemplate.queryForList(
        "select lower(column_name) from information_schema.columns where lower(table_name) = ?",
        String.class,
        table.toLowerCase());
  }
}
