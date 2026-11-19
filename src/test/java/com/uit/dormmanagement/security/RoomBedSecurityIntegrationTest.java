package com.uit.dormmanagement.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomBedSecurityIntegrationTest {

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private UserTokenInvalidationService userTokenInvalidationService;

  private User managerUser;
  private User tenantUser;
  private User technicianUser;

  @BeforeEach
  void setUp() {
    managerUser =
        userRepository.save(
            User.builder()
                .email("manager_room_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    tenantUser =
        userRepository.save(
            User.builder()
                .email("tenant_room_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    technicianUser =
        userRepository.save(
            User.builder()
                .email("tech_room_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());
  }

  @Test
  @DisplayName("Anonymous requesting admin room routes should return 401")
  void anonymousRequestingAdminRoomsShouldReturn401() throws Exception {
    mockMvc.perform(get("/api/v1/admin/rooms")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/admin/rooms/archived")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/admin/rooms/matrix")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Tenant requesting admin room routes should return 403")
  void tenantRequestingAdminRoomsShouldReturn403() throws Exception {
    String gen = userTokenInvalidationService.getOrCreateGeneration(tenantUser.getId());
    String token = jwtService.generateAccessToken(tenantUser, UUID.randomUUID().toString(), gen);

    mockMvc
        .perform(get("/api/v1/admin/rooms").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/admin/rooms/archived").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Technician requesting admin room routes should return 403")
  void technicianRequestingAdminRoomsShouldReturn403() throws Exception {
    String gen = userTokenInvalidationService.getOrCreateGeneration(technicianUser.getId());
    String token = jwtService.generateAccessToken(technicianUser, UUID.randomUUID().toString(), gen);

    mockMvc
        .perform(get("/api/v1/admin/rooms").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Manager requesting admin room routes should return 200")
  void managerRequestingAdminRoomsShouldReturn200() throws Exception {
    String gen = userTokenInvalidationService.getOrCreateGeneration(managerUser.getId());
    String token = jwtService.generateAccessToken(managerUser, UUID.randomUUID().toString(), gen);

    mockMvc
        .perform(get("/api/v1/admin/rooms").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/admin/rooms/archived").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/admin/rooms/matrix").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
