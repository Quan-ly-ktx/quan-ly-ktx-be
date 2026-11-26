package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.auth.UserProfileResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.TenantService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
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
class TenantProfileControllerTest {

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ResponseFactory responseFactory = new ResponseFactory();

  @Mock private TenantService tenantService;

  @InjectMocks private TenantProfileController tenantProfileController;

  private UUID tenantUserId;
  private UserPrincipal principal;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(tenantProfileController, "responseFactory", responseFactory);
    tenantUserId = UUID.randomUUID();
    User tenantUser =
        User.builder()
            .id(tenantUserId)
            .email("tenant@dorm.vn")
            .passwordHash("$2a$12$hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    principal = UserPrincipal.create(tenantUser);

    HandlerMethodArgumentResolver principalResolver =
        new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
          }

          @Override
          public Object resolveArgument(
              MethodParameter parameter,
              ModelAndViewContainer mavContainer,
              NativeWebRequest webRequest,
              WebDataBinderFactory binderFactory) {
            return principal;
          }
        };

    mockMvc =
        MockMvcBuilders.standaloneSetup(tenantProfileController)
            .setCustomArgumentResolvers(principalResolver)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();
  }

  @Test
  @DisplayName("PATCH /api/v1/tenant/profile: should succeed and return updated user profile")
  void updateOwnContact_whenValid_shouldReturn200() throws Exception {
    TenantContactUpdateRequest request =
        TenantContactUpdateRequest.builder()
            .phone("0987654321")
            .permanentAddress("123 Vo Van Ngan, Thu Duc, TP.HCM")
            .emergencyContactName("Nguyen Van Parent")
            .emergencyContactPhone("0912345678")
            .build();

    UserProfileResponse.TenantInfo tenantInfo =
        new UserProfileResponse.TenantInfo(
            UUID.randomUUID(),
            "NT-1234",
            "Nguyen Van Tenant",
            "MALE",
            "079203001234",
            "0987654321",
            "tenant@dorm.vn",
            "123 Vo Van Ngan, Thu Duc, TP.HCM",
            LocalDate.of(2003, 1, 1),
            "Nguyen Van Parent",
            "0912345678",
            ResidentStatus.ACTIVE);

    UserProfileResponse response =
        new UserProfileResponse(
            tenantUserId, "tenant@dorm.vn", UserRole.TENANT, UserStatus.ACTIVE, null, tenantInfo);

    when(tenantService.updateOwnContact(eq(tenantUserId), any())).thenReturn(response);

    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.email").value("tenant@dorm.vn"))
        .andExpect(jsonPath("$.data.tenantProfile.phone").value("0987654321"))
        .andExpect(
            jsonPath("$.data.tenantProfile.permanentAddress")
                .value("123 Vo Van Ngan, Thu Duc, TP.HCM"));
  }

  @Test
  @DisplayName("PATCH /api/v1/tenant/profile: when invalid phone format should return 400")
  void updateOwnContact_whenInvalidPhone_shouldReturn400() throws Exception {
    TenantContactUpdateRequest request =
        TenantContactUpdateRequest.builder().phone("invalid-phone").build();

    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400));
  }

  @Test
  @DisplayName(
      "PATCH /api/v1/tenant/profile: blank phone or permanentAddress returns 400 VALIDATION_FAILED")
  void updateOwnContact_whenBlankRequiredFields_shouldReturn400() throws Exception {
    TenantContactUpdateRequest blankAddressReq =
        TenantContactUpdateRequest.builder().permanentAddress("   ").build();

    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankAddressReq)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));

    TenantContactUpdateRequest blankPhoneReq =
        TenantContactUpdateRequest.builder().phone("").build();

    mockMvc
        .perform(
            patch("/api/v1/tenant/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankPhoneReq)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
  }
}
