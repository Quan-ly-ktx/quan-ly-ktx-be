package com.uit.dormmanagement.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.dtos.request.auth.LoginRequest;
import com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.RoomCodeRegistry;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.security.JwtService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Phase2CoreModulesE2ETest {

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtService jwtService;
  @Autowired private UserTokenInvalidationService userTokenInvalidationService;

  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private RoomRepository roomRepository;
  @Autowired private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private VehicleChangeRequestRepository vehicleChangeRequestRepository;

  private User managerUser;
  private User tenantUser;
  private User technicianUser;
  private Tenant tenant;
  private Room room;
  private Bed bed;

  @BeforeEach
  void setUp() {
    managerUser =
        userRepository.save(
            User.builder()
                .email("e2e_manager@dorm.vn")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    staffProfileRepository.save(
        StaffProfile.builder()
            .user(managerUser)
            .fullName("E2E Manager")
            .phone("0988112233")
            .build());

    technicianUser =
        userRepository.save(
            User.builder()
                .email("e2e_tech@dorm.vn")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());

    tenantUser =
        userRepository.save(
            User.builder()
                .email("e2e_tenant@dorm.vn")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    tenant =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-E2E-01")
                .fullName("E2E Tenant")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203001122")
                .phone("0911002233")
                .permanentAddress("123 Vo Van Ngan, Thu Duc")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.E2E.101")
                .genderPolicy(GenderPolicy.MALE)
                .basePrice(BigDecimal.valueOf(1500000))
                .floor(1)
                .totalBeds((short) 4)
                .build());

    roomCodeRegistryRepository.save(
        RoomCodeRegistry.builder()
            .code("P.E2E.101")
            .room(room)
            .current(true)
            .usedInBusiness(false)
            .build());

    bed =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("P.E2E.101-B1")
                .slotKey("B1")
                .status(BedStatus.AVAILABLE)
                .build());
  }

  private String tokenFor(User user) {
    String gen = userTokenInvalidationService.getOrCreateGeneration(user.getId());
    return jwtService.generateAccessToken(user, UUID.randomUUID().toString(), gen);
  }

  private MockMultipartFile validImage(String name, String filename) {
    byte[] jpegBytes =
        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    return new MockMultipartFile(name, filename, "image/jpeg", jpegBytes);
  }

  @Test
  @DisplayName("Scenario 1: Authentication-to-authorization cross-module enforcement")
  void scenario1_authToAuthorization_crossModules() throws Exception {
    // 1. Login with credentials
    LoginRequest loginReq = new LoginRequest("e2e_manager@dorm.vn", "Password@123");
    String loginJson = objectMapper.writeValueAsString(loginReq);

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.user.role").value("MANAGER"));

    // 2. Token-based authorization across module endpoints
    String managerToken = tokenFor(managerUser);
    String techToken = tokenFor(technicianUser);
    String tenantToken = tokenFor(tenantUser);

    // Manager can access admin rooms
    mockMvc
        .perform(get("/api/v1/admin/rooms").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    // Technician forbidden from admin rooms
    mockMvc
        .perform(get("/api/v1/admin/rooms").header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());

    // Tenant forbidden from admin rooms
    mockMvc
        .perform(get("/api/v1/admin/rooms").header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());

    // Tenant can access tenant vehicle me
    mockMvc
        .perform(get("/api/v1/tenant/vehicles/me").header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isOk());

    // Manager forbidden from tenant endpoints
    mockMvc
        .perform(
            get("/api/v1/tenant/vehicles/me").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    // 3. User lock immediately invalidates active sessions across all modules
    userTokenInvalidationService.invalidateAll(tenantUser.getId());
    mockMvc
        .perform(get("/api/v1/tenant/vehicles/me").header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Scenario 2: Room & Bed management state transitions and invariants")
  void scenario2_roomAndBedManagement_stateTransitions() throws Exception {
    String managerToken = tokenFor(managerUser);

    // Query room detail
    mockMvc
        .perform(
            get("/api/v1/admin/rooms/" + room.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roomNumber").value("P.E2E.101"))
        .andExpect(jsonPath("$.data.beds").isArray());

    // Bed status transition via repository
    bed.setStatus(BedStatus.OCCUPIED);
    bedRepository.save(bed);

    // Bed occupies room -> verify reflected
    Bed updatedBed = bedRepository.findById(bed.getId()).orElseThrow();
    assertThat(updatedBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);
  }

  @Test
  @DisplayName(
      "Scenario 3: Tenant self-service & Vehicle lifecycle with checked-out permission split")
  void scenario3_tenantSelfServiceAndVehicle_checkedOutSplit() throws Exception {
    String tenantToken = tokenFor(tenantUser);
    String managerToken = tokenFor(managerUser);

    // 1. Tenant updates own contact information
    TenantContactUpdateRequest contactReq =
        new TenantContactUpdateRequest("0911002233", "Updated Address 456", "Mom", "0900112233");
    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .header("Authorization", "Bearer " + tenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contactReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tenantProfile.permanentAddress").value("Updated Address 456"));

    // 2. Tenant registers a vehicle with photos
    MockMultipartFile front = validImage("frontImage", "f.jpg");
    MockMultipartFile back = validImage("backImage", "b.jpg");

    mockMvc
        .perform(
            multipart("/api/v1/tenant/vehicles")
                .file(front)
                .file(back)
                .param("vehicleType", "SCOOTER")
                .param("licensePlate", "59-E2 111.22")
                .param("brand", "Honda")
                .param("color", "White")
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING_CARD"));

    Vehicle regVehicle =
        vehicleRepository
            .findFirstByTenantIdAndStatusIn(
                tenant.getId(), java.util.List.of(VehicleStatus.PENDING_CARD))
            .orElseThrow();

    // 3. Manager issues card
    IssueCardRequest issueReq = new IssueCardRequest("CARD-E2E-001");
    mockMvc
        .perform(
            patch("/api/v1/admin/vehicles/" + regVehicle.getId() + "/issue-card")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(issueReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ISSUED"))
        .andExpect(jsonPath("$.data.cardCode").value("CARD-E2E-001"));

    // 4. Directly update tenant resident status to CHECKED_OUT
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    tenantRepository.save(tenant);

    // 5. Checked-out tenant can still view vehicle history
    mockMvc
        .perform(get("/api/v1/tenant/vehicles").header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());

    // 6. Checked-out tenant CANNOT register another vehicle (denied with 400)
    mockMvc
        .perform(
            multipart("/api/v1/tenant/vehicles")
                .file(front)
                .file(back)
                .param("vehicleType", "BICYCLE")
                .param("brand", "Giant")
                .param("color", "Black")
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Scenario 4: Atomic vehicle change request approval preserves card code and links")
  void scenario4_atomicVehicleChangeRequestApproval() throws Exception {
    String tenantToken = tokenFor(tenantUser);
    String managerToken = tokenFor(managerUser);

    // Initial vehicle issued
    Vehicle currentVehicle =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(tenant)
                .vehicleType(VehicleType.SCOOTER)
                .licensePlate("59-E2 333.44")
                .brand("Honda")
                .color("Red")
                .cardCode("CARD-E2E-REPLACE")
                .registrationFrontFileKey("vehicles/init/f.jpg")
                .registrationBackFileKey("vehicles/init/b.jpg")
                .status(VehicleStatus.ISSUED)
                .issuedAt(Instant.now().minusSeconds(7200))
                .build());

    // Tenant creates change request
    MockMultipartFile front = validImage("frontImage", "new_f.jpg");
    MockMultipartFile back = validImage("backImage", "new_b.jpg");

    mockMvc
        .perform(
            multipart("/api/v1/tenant/vehicle-change-requests")
                .file(front)
                .file(back)
                .param("currentVehicleId", currentVehicle.getId().toString())
                .param("proposedVehicleType", "MANUAL_MOTORCYCLE")
                .param("proposedLicensePlate", "59-E2 555.66")
                .param("proposedBrand", "Yamaha")
                .param("proposedColor", "Blue")
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING"));

    VehicleChangeRequest req =
        vehicleChangeRequestRepository
            .findFirstByTenantIdAndStatus(tenant.getId(), VehicleChangeRequestStatus.PENDING)
            .orElseThrow();

    // Manager approves change request
    mockMvc
        .perform(
            patch("/api/v1/admin/vehicle-change-requests/" + req.getId() + "/approve")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.newVehicleId").isNotEmpty());

    // Verify database facts
    Vehicle oldVeh = vehicleRepository.findById(currentVehicle.getId()).orElseThrow();
    assertThat(oldVeh.getStatus()).isEqualTo(VehicleStatus.REPLACED);
    assertThat(oldVeh.getCardCode()).isEqualTo("CARD-E2E-REPLACE");
    assertThat(oldVeh.getReplacedByVehicle()).isNotNull();

    Vehicle newVeh =
        vehicleRepository.findById(oldVeh.getReplacedByVehicle().getId()).orElseThrow();
    assertThat(newVeh.getStatus()).isEqualTo(VehicleStatus.ISSUED);
    assertThat(newVeh.getCardCode()).isEqualTo("CARD-E2E-REPLACE"); // Exact same RFID card code
    assertThat(newVeh.getLicensePlate()).isEqualTo("59-E2 555.66");
    assertThat(newVeh.getReplacedVehicle().getId()).isEqualTo(oldVeh.getId());
  }
}
