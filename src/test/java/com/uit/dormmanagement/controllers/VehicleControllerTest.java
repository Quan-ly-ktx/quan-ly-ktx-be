package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.VehicleService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

  @Mock private VehicleService vehicleService;

  @InjectMocks private AdminVehicleController adminVehicleController;
  @InjectMocks private TenantVehicleController tenantVehicleController;

  private MockMvc adminMockMvc;
  private MockMvc tenantMockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final ResponseFactory responseFactory = new ResponseFactory();

  private UUID vehicleId;
  private UUID tenantId;
  private UUID userId;
  private VehicleResponse sampleResponse;
  private UserPrincipal userPrincipal;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(adminVehicleController, "responseFactory", responseFactory);
    ReflectionTestUtils.setField(tenantVehicleController, "responseFactory", responseFactory);

    vehicleId = UUID.randomUUID();
    tenantId = UUID.randomUUID();
    userId = UUID.randomUUID();

    User user =
        User.builder()
            .id(userId)
            .username("nam.nguyen@example.com")
            .email("nam.nguyen@example.com")
            .phone("0987654321")
            .passwordHash("hashed")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    userPrincipal = UserPrincipal.create(user);

    HandlerMethodArgumentResolver principalResolver =
        new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                && parameter.getParameterType().isAssignableFrom(UserPrincipal.class);
          }

          @Override
          public Object resolveArgument(
              MethodParameter parameter,
              ModelAndViewContainer mavContainer,
              NativeWebRequest webRequest,
              WebDataBinderFactory binderFactory) {
            return userPrincipal;
          }
        };

    adminMockMvc =
        MockMvcBuilders.standaloneSetup(adminVehicleController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    tenantMockMvc =
        MockMvcBuilders.standaloneSetup(tenantVehicleController)
            .setCustomArgumentResolvers(principalResolver)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    sampleResponse =
        VehicleResponse.builder()
            .id(vehicleId)
            .tenantId(tenantId)
            .tenantName("Nguyễn Hoàng Nam")
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X3 789.12")
            .brand("Honda")
            .color("Đen")
            .cardCode("THE-0187")
            .status(VehicleStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/vehicles: should return paged vehicles")
  void getVehicles_shouldReturnPaged() throws Exception {
    Page<VehicleResponse> page = new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 10), 1);
    when(vehicleService.getVehicles(any(), any(), any())).thenReturn(page);

    adminMockMvc
        .perform(get("/api/v1/admin/vehicles").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.items[0].licensePlate").value("59-X3 789.12"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/vehicles: should create vehicle and return 201")
  void createVehicleAdmin_shouldReturn201() throws Exception {
    CreateVehicleAdminRequest request =
        CreateVehicleAdminRequest.builder()
            .tenantId(tenantId)
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X3 789.12")
            .brand("Honda")
            .color("Đen")
            .cardCode("THE-0187")
            .build();

    when(vehicleService.createVehicleAdmin(any())).thenReturn(sampleResponse);

    adminMockMvc
        .perform(
            post("/api/v1/admin/vehicles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.data.licensePlate").value("59-X3 789.12"));
  }

  @Test
  @DisplayName("PATCH /api/v1/admin/vehicles/{id}/issue-card: should issue card and return 200")
  void issueCard_shouldReturn200() throws Exception {
    IssueCardRequest req = IssueCardRequest.builder().cardCode("THE-0187").build();
    when(vehicleService.issueCard(eq(vehicleId), any())).thenReturn(sampleResponse);

    adminMockMvc
        .perform(
            patch("/api/v1/admin/vehicles/{id}/issue-card", vehicleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.cardCode").value("THE-0187"));
  }

  @Test
  @DisplayName("PATCH /api/v1/admin/vehicles/{id}/revoke-card: should revoke card and return 200")
  void revokeCard_shouldReturn200() throws Exception {
    VehicleResponse revoked =
        VehicleResponse.builder().id(vehicleId).status(VehicleStatus.REVOKED).build();
    when(vehicleService.revokeCard(vehicleId)).thenReturn(revoked);

    adminMockMvc
        .perform(patch("/api/v1/admin/vehicles/{id}/revoke-card", vehicleId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.status").value("REVOKED"));
  }

  @Test
  @DisplayName("GET /api/v1/tenant/vehicles/me: should return logged-in tenant vehicle")
  void getMyVehicle_shouldReturnVehicle() throws Exception {
    when(vehicleService.getMyVehicle(userId)).thenReturn(sampleResponse);

    tenantMockMvc
        .perform(get("/api/v1/tenant/vehicles/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.licensePlate").value("59-X3 789.12"));
  }

  @Test
  @DisplayName("POST /api/v1/tenant/vehicles: multipart register vehicle should return 201")
  void registerVehicle_shouldReturn201() throws Exception {
    MockMultipartFile front =
        new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", new byte[] {1, 2});
    MockMultipartFile back =
        new MockMultipartFile("backImage", "back.jpg", "image/jpeg", new byte[] {3, 4});

    VehicleResponse pending =
        VehicleResponse.builder()
            .id(vehicleId)
            .status(VehicleStatus.PENDING)
            .licensePlate("59-X3 999.99")
            .build();

    when(vehicleService.registerVehicle(eq(userId), any(), any(), any())).thenReturn(pending);

    tenantMockMvc
        .perform(
            multipart("/api/v1/tenant/vehicles")
                .file(front)
                .file(back)
                .param("vehicleType", "SCOOTER")
                .param("licensePlate", "59-X3 999.99")
                .param("brand", "Honda")
                .param("color", "Đỏ"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.data.status").value("PENDING"));
  }
}
