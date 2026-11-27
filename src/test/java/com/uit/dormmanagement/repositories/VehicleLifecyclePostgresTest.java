package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleChangeRequestResponse;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.services.VehicleChangeRequestService;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Transactional
class VehicleLifecyclePostgresTest {

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

  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private VehicleChangeRequestRepository vehicleChangeRequestRepository;
  @Autowired private VehicleChangeRequestService vehicleChangeRequestService;
  @Autowired private EntityManager entityManager;

  private User managerUser;
  private User tenantUser;
  private StaffProfile managerStaff;
  private Tenant tenant;
  private Vehicle activeVehicle;

  @BeforeEach
  void setUp() {
    managerUser =
        userRepository.save(
            User.builder()
                .email("mgr_veh_pg@test.vn")
                .passwordHash("hash")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    managerStaff =
        staffProfileRepository.save(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Manager Veh Test")
                .phone("0988000111")
                .build());

    tenantUser =
        userRepository.save(
            User.builder()
                .email("tenant_veh_pg@test.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    tenant =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-VEH-01")
                .fullName("Tenant Veh Test")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203998877")
                .phone("0911999888")
                .permanentAddress("123 Saigon")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    activeVehicle =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(tenant)
                .vehicleType(VehicleType.SCOOTER)
                .licensePlate("59-X1 999.01")
                .brand("Honda")
                .color("Red")
                .cardCode("CARD-PG-001")
                .registrationFrontFileKey("vehicles/active/front.jpg")
                .registrationBackFileKey("vehicles/active/back.jpg")
                .status(VehicleStatus.ISSUED)
                .issuedAt(Instant.now().minusSeconds(3600))
                .build());

    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("Postgres partial unique index prevents two active vehicles for same tenant")
  void postgresConstraint_preventsTwoActiveVehiclesForSameTenant() {
    Vehicle secondVehicle =
        Vehicle.builder()
            .tenant(tenant)
            .vehicleType(VehicleType.BICYCLE)
            .brand("Giant")
            .color("Blue")
            .status(VehicleStatus.PENDING_CARD)
            .build();

    assertThatThrownBy(
            () -> {
              vehicleRepository.save(secondVehicle);
              entityManager.flush();
            })
        .isInstanceOf(Exception.class)
        .hasMessageContaining("uq_vehicles_tenant_active_quota");
  }

  @Test
  @DisplayName("Postgres partial unique index prevents duplicate cardCode among ISSUED vehicles")
  void postgresConstraint_preventsDuplicateActiveCardCode() {
    User otherUser =
        userRepository.save(
            User.builder()
                .email("other_veh@test.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant otherTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(otherUser)
                .tenantCode("NT-VEH-02")
                .fullName("Other Tenant")
                .gender(GenderPolicy.FEMALE)
                .idCardNumber("079203998878")
                .phone("0911999889")
                .permanentAddress("456 Hanoi")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    Vehicle otherVehicle =
        Vehicle.builder()
            .tenant(otherTenant)
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X2 999.02")
            .brand("Yamaha")
            .color("Black")
            .registrationFrontFileKey("vehicles/other/front.jpg")
            .registrationBackFileKey("vehicles/other/back.jpg")
            .cardCode("CARD-PG-001") // Duplicate active card code
            .status(VehicleStatus.ISSUED)
            .issuedAt(Instant.now())
            .build();

    assertThatThrownBy(
            () -> {
              vehicleRepository.save(otherVehicle);
              entityManager.flush();
            })
        .isInstanceOf(Exception.class)
        .hasMessageContaining("uq_vehicles_issued_card_code");
  }

  @Test
  @DisplayName("Postgres partial unique index prevents two PENDING change requests for same tenant")
  void postgresConstraint_preventsTwoPendingChangeRequests() {
    VehicleChangeRequest req1 =
        vehicleChangeRequestRepository.save(
            VehicleChangeRequest.builder()
                .tenant(tenant)
                .currentVehicle(activeVehicle)
                .proposedVehicleType(VehicleType.MANUAL_MOTORCYCLE)
                .proposedLicensePlate("59-X1 222.22")
                .proposedBrand("Honda")
                .proposedColor("Black")
                .proposedRegistrationFrontFileKey("vehicles/req1/front.jpg")
                .proposedRegistrationBackFileKey("vehicles/req1/back.jpg")
                .status(VehicleChangeRequestStatus.PENDING)
                .build());

    VehicleChangeRequest req2 =
        VehicleChangeRequest.builder()
            .tenant(tenant)
            .currentVehicle(activeVehicle)
            .proposedVehicleType(VehicleType.BICYCLE)
            .proposedBrand("Martin")
            .proposedColor("Red")
            .status(VehicleChangeRequestStatus.PENDING)
            .build();

    assertThatThrownBy(
            () -> {
              vehicleChangeRequestRepository.save(req2);
              entityManager.flush();
            })
        .isInstanceOf(Exception.class)
        .hasMessageContaining("uq_vehicle_change_requests_pending_tenant");
  }

  @Test
  @DisplayName("Approval atomically replaces old vehicle with successor using exact same Card Code")
  void approval_atomicallyReplacesOldVehicleWithSameCardCode() {
    VehicleChangeRequest req =
        vehicleChangeRequestRepository.save(
            VehicleChangeRequest.builder()
                .tenant(tenant)
                .currentVehicle(activeVehicle)
                .proposedVehicleType(VehicleType.MANUAL_MOTORCYCLE)
                .proposedLicensePlate("59-X1 888.88")
                .proposedBrand("Yamaha")
                .proposedColor("Matte Black")
                .proposedRegistrationFrontFileKey("vehicles/req/front.jpg")
                .proposedRegistrationBackFileKey("vehicles/req/back.jpg")
                .status(VehicleChangeRequestStatus.PENDING)
                .build());

    entityManager.flush();

    VehicleChangeRequestResponse approvedRes =
        vehicleChangeRequestService.approve(managerUser.getId(), req.getId());

    assertThat(approvedRes.status()).isEqualTo(VehicleChangeRequestStatus.APPROVED);
    assertThat(approvedRes.newVehicleId()).isNotNull();

    entityManager.flush();
    entityManager.clear();

    Vehicle oldVeh = vehicleRepository.findById(activeVehicle.getId()).orElseThrow();
    assertThat(oldVeh.getStatus()).isEqualTo(VehicleStatus.REPLACED);
    assertThat(oldVeh.getCardCode()).isEqualTo("CARD-PG-001");
    assertThat(oldVeh.getReplacedByVehicle()).isNotNull();

    Vehicle newVeh = vehicleRepository.findById(approvedRes.newVehicleId()).orElseThrow();
    assertThat(newVeh.getStatus()).isEqualTo(VehicleStatus.ISSUED);
    assertThat(newVeh.getCardCode()).isEqualTo("CARD-PG-001"); // Preserved card code!
    assertThat(newVeh.getLicensePlate()).isEqualTo("59-X1 888.88");
    assertThat(newVeh.getReplacedVehicle().getId()).isEqualTo(oldVeh.getId());
  }
}
