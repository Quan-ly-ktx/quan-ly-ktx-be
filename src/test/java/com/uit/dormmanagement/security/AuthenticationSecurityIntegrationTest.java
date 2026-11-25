package com.uit.dormmanagement.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.TokenRevocationService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationSecurityIntegrationTest {

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private UserTokenInvalidationService userTokenInvalidationService;
  @Autowired private TokenRevocationService tokenRevocationService;

  private User managerUser;
  private User tenantUser;

  @BeforeEach
  void setUp() {
    managerUser =
        userRepository.save(
            User.builder()
                .email("manager_sec_test@example.com")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    tenantUser =
        userRepository.save(
            User.builder()
                .email("tenant_sec_test@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
  }

  @Test
  @DisplayName("Anonymous protected request should return 401")
  void anonymousProtectedRequestShouldReturn401() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant requesting Manager-only technician route should return 403")
  void tenantRequestingManagerRouteShouldReturn403() throws Exception {
    String gen = userTokenInvalidationService.getOrCreateGeneration(tenantUser.getId());
    String token = jwtService.generateAccessToken(tenantUser, UUID.randomUUID().toString(), gen);

    mockMvc
        .perform(get("/api/v1/users/technicians").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Manager requesting Manager-only technician route should return 200")
  void managerRequestingManagerRouteShouldReturn200() throws Exception {
    String gen = userTokenInvalidationService.getOrCreateGeneration(managerUser.getId());
    String token = jwtService.generateAccessToken(managerUser, UUID.randomUUID().toString(), gen);

    mockMvc
        .perform(get("/api/v1/users/technicians").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Locked user token should return 401 on protected route")
  void lockedUserTokenShouldReturn401() throws Exception {
    String gen = userTokenInvalidationService.getOrCreateGeneration(tenantUser.getId());
    String token = jwtService.generateAccessToken(tenantUser, UUID.randomUUID().toString(), gen);

    tenantUser.setStatus(UserStatus.LOCKED);
    userRepository.saveAndFlush(tenantUser);

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Blacklisted token JTI should return 401")
  void blacklistedTokenShouldReturn401() throws Exception {
    String jti = UUID.randomUUID().toString();
    String gen = userTokenInvalidationService.getOrCreateGeneration(managerUser.getId());
    String token = jwtService.generateAccessToken(managerUser, jti, gen);

    tokenRevocationService.blacklistAccessToken(jti, 600000L);

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Token with old session generation should return 401")
  void oldSessionGenerationTokenShouldReturn401() throws Exception {
    String gen = userTokenInvalidationService.getOrCreateGeneration(managerUser.getId());
    String token = jwtService.generateAccessToken(managerUser, UUID.randomUUID().toString(), gen);

    userTokenInvalidationService.invalidateAll(managerUser.getId());

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Token missing session generation claim should return 401")
  void missingSessionGenerationTokenShouldReturn401() throws Exception {
    String tokenWithoutGen =
        jwtService.generateAccessToken(managerUser, UUID.randomUUID().toString(), null);

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenWithoutGen))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Manager email update route should not exist")
  void managerEmailUpdateRouteShouldNotExist() throws Exception {
    String gen = userTokenInvalidationService.getOrCreateGeneration(managerUser.getId());
    String token = jwtService.generateAccessToken(managerUser, UUID.randomUUID().toString(), gen);

    mockMvc
        .perform(
            put("/api/v1/users/me/email")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"email\":\"newemail@example.com\"}"))
        .andExpect(status().isNotFound());
  }
}
