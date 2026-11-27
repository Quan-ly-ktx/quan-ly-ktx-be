package com.uit.dormmanagement.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantProfileSecurityIntegrationTest {

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private UserTokenInvalidationService userTokenInvalidationService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private User managerUser;
  private User tenantUser;
  private User checkedOutTenantUser;
  private User technicianUser;
  private Tenant activeTenant;
  private Tenant checkedOutTenant;

  @BeforeEach
  void setUp() {
    managerUser =
        userRepository.save(
            User.builder()
                .email("manager_tenant_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    staffProfileRepository.save(
        StaffProfile.builder()
            .user(managerUser)
            .fullName("Manager Test")
            .phone("0988776655")
            .build());

    tenantUser =
        userRepository.save(
            User.builder()
                .email("tenant_profile_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    activeTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-8888")
                .fullName("Active Tenant Sec")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203888888")
                .phone("0911222333")
                .permanentAddress("123 Vo Van Ngan")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    checkedOutTenantUser =
        userRepository.save(
            User.builder()
                .email("checkedout_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    checkedOutTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(checkedOutTenantUser)
                .tenantCode("NT-8889")
                .fullName("Checked Out Tenant Sec")
                .gender(GenderPolicy.FEMALE)
                .idCardNumber("079203888889")
                .phone("0944555666")
                .permanentAddress("456 Dang Van Bi")
                .residentStatus(ResidentStatus.CHECKED_OUT)
                .build());

    technicianUser =
        userRepository.save(
            User.builder()
                .email("tech_tenant_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());
  }

  private String tokenFor(User user) {
    String gen = userTokenInvalidationService.getOrCreateGeneration(user.getId());
    return jwtService.generateAccessToken(user, UUID.randomUUID().toString(), gen);
  }

  @Test
  @DisplayName("Anonymous requesting admin tenant endpoints should return 401")
  void anonymous_requestingAdminTenants_shouldReturn401() throws Exception {
    mockMvc.perform(get("/api/v1/admin/tenants")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/admin/tenants/" + activeTenant.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/v1/admin/tenants/" + activeTenant.getId() + "/reset-password"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant requesting admin tenant endpoints should return 403")
  void tenant_requestingAdminTenants_shouldReturn403() throws Exception {
    String token = tokenFor(tenantUser);
    mockMvc
        .perform(get("/api/v1/admin/tenants").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/admin/tenants/" + activeTenant.getId() + "/reset-password")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Technician requesting admin tenant endpoints should return 403")
  void technician_requestingAdminTenants_shouldReturn403() throws Exception {
    String token = tokenFor(technicianUser);
    mockMvc
        .perform(get("/api/v1/admin/tenants").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Manager requesting admin tenant endpoints should succeed")
  void manager_requestingAdminTenants_shouldSucceed() throws Exception {
    String token = tokenFor(managerUser);
    mockMvc
        .perform(get("/api/v1/admin/tenants").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/admin/tenants/" + activeTenant.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Anonymous requesting tenant profile endpoint should return 401")
  void anonymous_requestingTenantProfile_shouldReturn401() throws Exception {
    TenantContactUpdateRequest req =
        TenantContactUpdateRequest.builder().phone("0988111222").build();
    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Manager requesting tenant profile endpoint should return 403")
  void manager_requestingTenantProfile_shouldReturn403() throws Exception {
    String token = tokenFor(managerUser);
    TenantContactUpdateRequest req =
        TenantContactUpdateRequest.builder().phone("0988111222").build();
    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Tenant requesting tenant profile endpoint should succeed")
  void tenant_requestingTenantProfile_shouldSucceed() throws Exception {
    String token = tokenFor(tenantUser);
    TenantContactUpdateRequest req =
        TenantContactUpdateRequest.builder()
            .phone("0988111222")
            .permanentAddress("Updated Address")
            .build();
    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Checked-out tenant requesting own profile endpoint should succeed")
  void checkedOutTenant_requestingTenantProfile_shouldSucceed() throws Exception {
    String token = tokenFor(checkedOutTenantUser);
    TenantContactUpdateRequest req =
        TenantContactUpdateRequest.builder()
            .phone("0988333444")
            .permanentAddress("Checked Out Address")
            .build();
    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());
  }
}
