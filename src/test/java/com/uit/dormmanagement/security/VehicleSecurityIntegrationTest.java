package com.uit.dormmanagement.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.services.VehicleEvidenceStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VehicleSecurityIntegrationTest {

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private VehicleChangeRequestRepository vehicleChangeRequestRepository;
  @Autowired private VehicleEvidenceStorage vehicleEvidenceStorage;
  @Autowired private UserTokenInvalidationService userTokenInvalidationService;

  private User managerUser;
  private User tenantUser1;
  private User tenantUser2;
  private User checkedOutUser;
  private User technicianUser;

  private Tenant tenant1;
  private Tenant tenant2;
  private Tenant checkedOutTenant;

  private Vehicle tenant1Vehicle;
  private VehicleChangeRequest tenant1Request;
  private String storedFrontKey;

  private String tokenFor(User user) {
    String gen = userTokenInvalidationService.getOrCreateGeneration(user.getId());
    return jwtService.generateAccessToken(user, UUID.randomUUID().toString(), gen);
  }

  @BeforeEach
  void setUp() {
    managerUser =
        userRepository.save(
            User.builder()
                .email("veh_manager_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    staffProfileRepository.save(
        StaffProfile.builder()
            .user(managerUser)
            .fullName("Manager Veh Sec")
            .phone("0988001122")
            .build());

    tenantUser1 =
        userRepository.save(
            User.builder()
                .email("veh_tenant1_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    tenant1 =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser1)
                .tenantCode("NT-VSEC-01")
                .fullName("Tenant 1 Sec")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203111111")
                .phone("0911001101")
                .permanentAddress("123 Street A")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    tenantUser2 =
        userRepository.save(
            User.builder()
                .email("veh_tenant2_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    tenant2 =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser2)
                .tenantCode("NT-VSEC-02")
                .fullName("Tenant 2 Sec")
                .gender(GenderPolicy.FEMALE)
                .idCardNumber("079203222222")
                .phone("0911001102")
                .permanentAddress("456 Street B")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    checkedOutUser =
        userRepository.save(
            User.builder()
                .email("veh_checkedout_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    checkedOutTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(checkedOutUser)
                .tenantCode("NT-VSEC-03")
                .fullName("Checked Out Tenant")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203333333")
                .phone("0911001103")
                .permanentAddress("789 Street C")
                .residentStatus(ResidentStatus.CHECKED_OUT)
                .build());

    technicianUser =
        userRepository.save(
            User.builder()
                .email("veh_tech_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());

    // Create a dummy stored evidence file
    MockMultipartFile dummyFile =
        new MockMultipartFile(
            "frontImage",
            "reg-front.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "dummy image bytes".getBytes());
    UUID storageId = UUID.randomUUID();
    storedFrontKey = vehicleEvidenceStorage.store(storageId, "front", dummyFile);

    tenant1Vehicle =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(tenant1)
                .vehicleType(VehicleType.SCOOTER)
                .licensePlate("59-S1 111.11")
                .brand("Honda")
                .color("White")
                .cardCode("CARD-SEC-01")
                .registrationFrontFileKey(storedFrontKey)
                .registrationBackFileKey(null)
                .status(VehicleStatus.ISSUED)
                .issuedAt(Instant.now())
                .build());

    tenant1Request =
        vehicleChangeRequestRepository.save(
            VehicleChangeRequest.builder()
                .tenant(tenant1)
                .currentVehicle(tenant1Vehicle)
                .proposedVehicleType(VehicleType.MANUAL_MOTORCYCLE)
                .proposedLicensePlate("59-S1 222.22")
                .proposedBrand("Yamaha")
                .proposedColor("Blue")
                .proposedRegistrationFrontFileKey(storedFrontKey)
                .proposedRegistrationBackFileKey(null)
                .status(VehicleChangeRequestStatus.PENDING)
                .build());
  }

  @Test
  @DisplayName("Anonymous access is denied (401)")
  void anonymous_accessDenied() throws Exception {
    mockMvc.perform(get("/api/v1/admin/vehicles")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/tenant/vehicles/me")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/admin/vehicle-change-requests"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/tenant/vehicle-change-requests"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("RBAC role restrictions are strictly enforced (403)")
  void rbac_roleRestrictionsEnforced() throws Exception {
    String tenantToken = tokenFor(tenantUser1);
    String managerToken = tokenFor(managerUser);
    String techToken = tokenFor(technicianUser);

    // Tenant cannot access admin endpoints
    mockMvc
        .perform(get("/api/v1/admin/vehicles").header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/admin/vehicle-change-requests")
                .header("Authorization", "Bearer " + tenantToken))
        .andExpect(status().isForbidden());

    // Manager cannot access tenant endpoints
    mockMvc
        .perform(
            get("/api/v1/tenant/vehicles/me").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    // Technician cannot access either
    mockMvc
        .perform(get("/api/v1/admin/vehicles").header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/tenant/vehicles/me").header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Cross-tenant evidence access returns 404 to avoid leaking existence")
  void evidence_crossTenantAccessReturns404() throws Exception {
    String tenant2Token = tokenFor(tenantUser2);

    // Tenant 2 attempts to fetch Tenant 1's vehicle evidence
    mockMvc
        .perform(
            get("/api/v1/tenant/vehicles/{id}/evidence/front", tenant1Vehicle.getId())
                .header("Authorization", "Bearer " + tenant2Token))
        .andExpect(status().isNotFound());

    // Tenant 2 attempts to fetch Tenant 1's change request evidence
    mockMvc
        .perform(
            get(
                    "/api/v1/tenant/vehicle-change-requests/{id}/evidence/front",
                    tenant1Request.getId())
                .header("Authorization", "Bearer " + tenant2Token))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Owner and Manager can stream vehicle and request evidence")
  void evidence_ownerAndManagerCanAccess() throws Exception {
    String tenant1Token = tokenFor(tenantUser1);
    String managerToken = tokenFor(managerUser);

    // Tenant 1 fetches own vehicle evidence
    mockMvc
        .perform(
            get("/api/v1/tenant/vehicles/{id}/evidence/front", tenant1Vehicle.getId())
                .header("Authorization", "Bearer " + tenant1Token))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE))
        .andExpect(content().bytes("dummy image bytes".getBytes()));

    // Manager fetches vehicle evidence
    mockMvc
        .perform(
            get("/api/v1/admin/vehicles/{id}/evidence/front", tenant1Vehicle.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE))
        .andExpect(content().bytes("dummy image bytes".getBytes()));

    // Tenant 1 fetches own change request evidence
    mockMvc
        .perform(
            get(
                    "/api/v1/tenant/vehicle-change-requests/{id}/evidence/front",
                    tenant1Request.getId())
                .header("Authorization", "Bearer " + tenant1Token))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE))
        .andExpect(content().bytes("dummy image bytes".getBytes()));

    // Manager fetches change request evidence
    mockMvc
        .perform(
            get("/api/v1/admin/vehicle-change-requests/{id}/evidence/front", tenant1Request.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE))
        .andExpect(content().bytes("dummy image bytes".getBytes()));
  }

  @Test
  @DisplayName("Missing evidence side returns 404")
  void evidence_missingSideReturns404() throws Exception {
    String tenant1Token = tokenFor(tenantUser1);

    mockMvc
        .perform(
            get("/api/v1/tenant/vehicles/{id}/evidence/back", tenant1Vehicle.getId())
                .header("Authorization", "Bearer " + tenant1Token))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Direct access to /uploads/** is denied")
  void directUploadsVehicles_isDenied() throws Exception {
    mockMvc
        .perform(get("/uploads/vehicles/any-file.jpg"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                    .isIn(401, 403));
    mockMvc
        .perform(get("/uploads/contracts/any-file.pdf"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                    .isIn(401, 403));
  }

  @Test
  @DisplayName("Checked-out tenant can view vehicle history but cannot register new vehicle")
  void checkedOutTenant_canViewHistoryButCannotRegister() throws Exception {
    String checkedOutToken = tokenFor(checkedOutUser);

    // Can view history
    mockMvc
        .perform(
            get("/api/v1/tenant/vehicles").header("Authorization", "Bearer " + checkedOutToken))
        .andExpect(status().isOk());

    // Cannot register
    MockMultipartFile front =
        new MockMultipartFile(
            "frontImage", "f.jpg", MediaType.IMAGE_JPEG_VALUE, "front".getBytes());
    MockMultipartFile back =
        new MockMultipartFile("backImage", "b.jpg", MediaType.IMAGE_JPEG_VALUE, "back".getBytes());

    mockMvc
        .perform(
            multipart("/api/v1/tenant/vehicles")
                .file(front)
                .file(back)
                .param("vehicleType", "SCOOTER")
                .param("licensePlate", "59-S1 999.99")
                .param("brand", "Honda")
                .param("color", "Red")
                .header("Authorization", "Bearer " + checkedOutToken))
        .andExpect(status().isBadRequest()); // TENANT_NOT_ACTIVE
  }
}
