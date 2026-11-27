package com.uit.dormmanagement.security;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.RoomCodeRegistry;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OnboardingSecurityIntegrationTest.TestClockConfig.class)
@Transactional
class OnboardingSecurityIntegrationTest {

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    Clock fixedDayOneClock() {
      return Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE);
    }
  }

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private RoomRepository roomRepository;
  @Autowired private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private UserTokenInvalidationService userTokenInvalidationService;

  private User managerUser;
  private User tenantUser;
  private User technicianUser;
  private Bed availableBed;

  @BeforeEach
  void setUp() {
    managerUser =
        userRepository.save(
            User.builder()
                .email("manager_onb_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    tenantUser =
        userRepository.save(
            User.builder()
                .email("tenant_onb_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    technicianUser =
        userRepository.save(
            User.builder()
                .email("tech_onb_sec@example.com")
                .passwordHash("hashed")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());

    Room room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.SEC.101")
                .floor(1)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds((short) 4)
                .basePrice(new BigDecimal("1500000"))
                .build());
    roomCodeRegistryRepository.save(
        RoomCodeRegistry.builder().code("P.SEC.101").room(room).current(true).build());

    List<Bed> beds = new ArrayList<>();
    for (int i = 1; i <= 4; i++) {
      String code = String.format("G%02d", i);
      beds.add(
          Bed.builder()
              .room(room)
              .bedCode(code)
              .slotKey("P.SEC.101-" + code)
              .status(BedStatus.AVAILABLE)
              .build());
    }
    availableBed = bedRepository.saveAll(beds).getFirst();
  }

  private String tokenFor(User user) {
    String gen = userTokenInvalidationService.getOrCreateGeneration(user.getId());
    return jwtService.generateAccessToken(user, UUID.randomUUID().toString(), gen);
  }

  private OnboardingRequest validRequest(LocalDate endDate) {
    return OnboardingRequest.builder()
        .fullName("Le Van Security")
        .gender(GenderPolicy.MALE)
        .idCardNumber("079203005566")
        .phone("0912345678")
        .email("levansec@example.com")
        .permanentAddress("123 Vo Van Ngan, Thu Duc")
        .bedId(availableBed.getId())
        .startDate(LocalDate.of(2026, 9, 1))
        .endDate(endDate)
        .depositAmount(new BigDecimal("1500000"))
        .build();
  }

  @Test
  @DisplayName("Anonymous POST /api/v1/admin/onboarding returns 401")
  void anonymousOnboarding_shouldReturn401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest(LocalDate.of(2027, 2, 28)))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("TENANT and TECHNICIAN POST /api/v1/admin/onboarding return 403")
  void nonManagerRolesOnboarding_shouldReturn403() throws Exception {
    String payload = objectMapper.writeValueAsString(validRequest(LocalDate.of(2027, 2, 28)));

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .header("Authorization", "Bearer " + tokenFor(tenantUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .header("Authorization", "Bearer " + tokenFor(technicianUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("MANAGER POST /api/v1/admin/onboarding succeeds with 201 and firstInvoice")
  void managerOnboarding_validDayOne_shouldReturn201() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .header("Authorization", "Bearer " + tokenFor(managerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest(LocalDate.of(2027, 2, 28)))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.data.tenant.idCardNumber").value("079203005566"))
        .andExpect(jsonPath("$.data.contract.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.account.username").value("079203005566"))
        .andExpect(jsonPath("$.data.account.initialPassword").isNotEmpty())
        .andExpect(jsonPath("$.data.account.initialPassword").value(not("Password123@")))
        .andExpect(jsonPath("$.data.firstInvoice.billingPeriod").value("2026-09-01"))
        .andExpect(jsonPath("$.data.firstInvoice.dueOn").value("2026-09-05"))
        .andExpect(jsonPath("$.data.firstInvoice.status").value("DEBT"))
        .andExpect(jsonPath("$.data.firstInvoice.amount").value(1500000));
  }

  @Test
  @DisplayName(
      "MANAGER POST /api/v1/admin/onboarding rejects invalid DTO and non-month-end endDate with 400")
  void managerOnboarding_invalidRequests_shouldReturn400() throws Exception {
    String managerToken = tokenFor(managerUser);

    OnboardingRequest invalidDto = OnboardingRequest.builder().fullName("").build();
    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest(LocalDate.of(2027, 9, 1)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("CONTRACT_400_DATES"));
  }
}
