package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uit.dormmanagement.dtos.request.tenant.UpdateTenantRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantSummaryResponse;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.services.TenantService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

  @Mock private TenantService tenantService;

  @InjectMocks private TenantController tenantController;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final ResponseFactory responseFactory = new ResponseFactory();

  private UUID tenantId;
  private TenantSummaryResponse sampleSummary;
  private TenantDetailResponse sampleDetail;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(tenantController, "responseFactory", responseFactory);
    mockMvc =
        MockMvcBuilders.standaloneSetup(tenantController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    tenantId = UUID.randomUUID();
    sampleSummary =
        TenantSummaryResponse.builder()
            .id(tenantId)
            .tenantCode("NT-0187")
            .fullName("Nguyễn Hoàng Nam")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203001187")
            .phone("0987654321")
            .email("nam.nguyen@example.com")
            .residentStatus(ResidentStatus.ACTIVE)
            .roomNumber("P.302")
            .bedCode("G02")
            .contractEndDate(LocalDate.of(2027, 9, 1))
            .build();

    sampleDetail =
        TenantDetailResponse.builder()
            .id(tenantId)
            .tenantCode("NT-0187")
            .fullName("Nguyễn Hoàng Nam")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203001187")
            .phone("0987654321")
            .email("nam.nguyen@example.com")
            .permanentAddress("123 Lê Lợi, TP.HCM")
            .residentStatus(ResidentStatus.ACTIVE)
            .currentRoomNumber("P.302")
            .currentBedCode("G02")
            .vehicles(List.of())
            .contractHistory(List.of())
            .build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/tenants: should return 200 and paged list")
  void getTenants_shouldReturnPagedResponse() throws Exception {
    Page<TenantSummaryResponse> pagedResponse =
        new PageImpl<>(List.of(sampleSummary), PageRequest.of(0, 10), 1);
    when(tenantService.getTenants(any(), any(), any(), any())).thenReturn(pagedResponse);

    mockMvc
        .perform(
            get("/api/v1/admin/tenants")
                .param("page", "0")
                .param("size", "10")
                .param("search", "Nam"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.items[0].tenantCode").value("NT-0187"))
        .andExpect(jsonPath("$.data.items[0].fullName").value("Nguyễn Hoàng Nam"))
        .andExpect(jsonPath("$.data.items[0].roomNumber").value("P.302"));
  }

  @Test
  @DisplayName("GET /api/v1/admin/tenants/{id}: when found should return 200 and detail")
  void getTenantById_whenFound_shouldReturnDetail() throws Exception {
    when(tenantService.getTenantById(tenantId)).thenReturn(sampleDetail);

    mockMvc
        .perform(get("/api/v1/admin/tenants/{id}", tenantId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.id").value(tenantId.toString()))
        .andExpect(jsonPath("$.data.fullName").value("Nguyễn Hoàng Nam"))
        .andExpect(jsonPath("$.data.currentRoomNumber").value("P.302"));
  }

  @Test
  @DisplayName("GET /api/v1/admin/tenants/{id}: when not found should return 404")
  void getTenantById_whenNotFound_shouldReturn404() throws Exception {
    when(tenantService.getTenantById(tenantId))
        .thenThrow(new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/admin/tenants/{id}", tenantId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.httpStatus").value(404))
        .andExpect(jsonPath("$.errorCode").value("TENANT_404"));
  }

  @Test
  @DisplayName("PUT /api/v1/admin/tenants/{id}: when valid should return 200 and updated detail")
  void updateTenant_whenValid_shouldReturn200() throws Exception {
    UpdateTenantRequest request =
        UpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam Cập Nhật")
            .phone("0912345678")
            .permanentAddress("456 Hai Bà Trưng, TP.HCM")
            .build();

    TenantDetailResponse updatedResponse =
        TenantDetailResponse.builder()
            .id(tenantId)
            .tenantCode("NT-0187")
            .fullName("Nguyễn Hoàng Nam Cập Nhật")
            .phone("0912345678")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203001187")
            .email("nam.nguyen@example.com")
            .residentStatus(ResidentStatus.ACTIVE)
            .currentRoomNumber("P.302")
            .build();

    when(tenantService.updateTenant(eq(tenantId), any())).thenReturn(updatedResponse);

    mockMvc
        .perform(
            put("/api/v1/admin/tenants/{id}", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.fullName").value("Nguyễn Hoàng Nam Cập Nhật"))
        .andExpect(jsonPath("$.data.phone").value("0912345678"));
  }

  @Test
  @DisplayName("PUT /api/v1/admin/tenants/{id}: when validation fails should return 400")
  void updateTenant_whenValidationFails_shouldReturn400() throws Exception {
    UpdateTenantRequest invalidRequest =
        UpdateTenantRequest.builder().fullName("").phone("12345").build();

    mockMvc
        .perform(
            put("/api/v1/admin/tenants/{id}", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400));
  }
}
