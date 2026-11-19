package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uit.dormmanagement.dtos.request.vehicle.RejectVehicleChangeRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleChangeRequestResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.VehicleChangeRequestService;
import com.uit.dormmanagement.services.VehicleEvidenceAccessService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class VehicleChangeRequestControllerTest {

  @Mock private VehicleChangeRequestService vehicleChangeRequestService;
  @Mock private VehicleEvidenceAccessService vehicleEvidenceAccessService;

  @InjectMocks private TenantVehicleChangeRequestController tenantController;
  @InjectMocks private AdminVehicleChangeRequestController adminController;

  private MockMvc tenantMockMvc;
  private MockMvc adminMockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final ResponseFactory responseFactory = new ResponseFactory();

  private UUID tenantUserId;
  private UUID requestId;
  private UUID currentVehicleId;
  private VehicleChangeRequestResponse sampleResponse;
  private UserPrincipal userPrincipal;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(tenantController, "responseFactory", responseFactory);
    ReflectionTestUtils.setField(adminController, "responseFactory", responseFactory);

    tenantUserId = UUID.randomUUID();
    requestId = UUID.randomUUID();
    currentVehicleId = UUID.randomUUID();

    User user =
        User.builder()
            .id(tenantUserId)
            .email("tenant@example.com")
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

    tenantMockMvc =
        MockMvcBuilders.standaloneSetup(tenantController)
            .setCustomArgumentResolvers(principalResolver)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    adminMockMvc =
        MockMvcBuilders.standaloneSetup(adminController)
            .setCustomArgumentResolvers(principalResolver, new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    sampleResponse =
        VehicleChangeRequestResponse.builder()
            .id(requestId)
            .tenantId(UUID.randomUUID())
            .tenantName("Nguyen Van A")
            .currentVehicleId(currentVehicleId)
            .currentVehicleLicensePlate("59-X1 111.11")
            .currentVehicleCardCode("CARD-001")
            .proposedVehicleType(VehicleType.MANUAL_MOTORCYCLE)
            .proposedLicensePlate("59-X2 222.22")
            .proposedBrand("Yamaha")
            .proposedColor("Xanh")
            .status(VehicleChangeRequestStatus.PENDING)
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("POST /api/v1/tenant/vehicle-change-requests: should return 201")
  void createChangeRequest_shouldReturn201() throws Exception {
    when(vehicleChangeRequestService.create(eq(tenantUserId), any(), any(), any()))
        .thenReturn(sampleResponse);

    tenantMockMvc
        .perform(
            multipart("/api/v1/tenant/vehicle-change-requests")
                .param("currentVehicleId", currentVehicleId.toString())
                .param("proposedVehicleType", "MANUAL_MOTORCYCLE")
                .param("proposedLicensePlate", "59-X2 222.22")
                .param("proposedBrand", "Yamaha")
                .param("proposedColor", "Xanh"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.data.proposedLicensePlate").value("59-X2 222.22"));
  }

  @Test
  @DisplayName("GET /api/v1/tenant/vehicle-change-requests: should return 200 and list")
  void getMyRequests_shouldReturnList() throws Exception {
    when(vehicleChangeRequestService.getTenantRequests(tenantUserId))
        .thenReturn(List.of(sampleResponse));

    tenantMockMvc
        .perform(get("/api/v1/tenant/vehicle-change-requests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data[0].id").value(requestId.toString()));
  }

  @Test
  @DisplayName("GET /api/v1/admin/vehicle-change-requests: should return paged 200")
  void getAdminRequests_shouldReturnPaged() throws Exception {
    Page<VehicleChangeRequestResponse> page =
        new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 10), 1);
    when(vehicleChangeRequestService.getAdminRequests(any(), any(), any())).thenReturn(page);

    adminMockMvc
        .perform(get("/api/v1/admin/vehicle-change-requests").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.items[0].id").value(requestId.toString()));
  }

  @Test
  @DisplayName("PATCH /api/v1/admin/vehicle-change-requests/{id}/approve: should return 200")
  void approveRequest_shouldReturn200() throws Exception {
    VehicleChangeRequestResponse approved =
        VehicleChangeRequestResponse.builder()
            .id(requestId)
            .status(VehicleChangeRequestStatus.APPROVED)
            .build();
    when(vehicleChangeRequestService.approve(any(), eq(requestId))).thenReturn(approved);

    adminMockMvc
        .perform(patch("/api/v1/admin/vehicle-change-requests/{id}/approve", requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.status").value("APPROVED"));
  }

  @Test
  @DisplayName("PATCH /api/v1/admin/vehicle-change-requests/{id}/reject: should return 200")
  void rejectRequest_shouldReturn200() throws Exception {
    RejectVehicleChangeRequest req = new RejectVehicleChangeRequest("Giấy tờ không hợp lệ");
    VehicleChangeRequestResponse rejected =
        VehicleChangeRequestResponse.builder()
            .id(requestId)
            .status(VehicleChangeRequestStatus.REJECTED)
            .rejectionReason("Giấy tờ không hợp lệ")
            .build();
    when(vehicleChangeRequestService.reject(any(), eq(requestId), eq("Giấy tờ không hợp lệ")))
        .thenReturn(rejected);

    adminMockMvc
        .perform(
            patch("/api/v1/admin/vehicle-change-requests/{id}/reject", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.status").value("REJECTED"));
  }
}
