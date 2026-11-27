package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.onboarding.GeneratedAccountInfo;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingInvoiceSummary;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.services.OnboardingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

  @Mock private OnboardingService onboardingService;

  @InjectMocks private OnboardingController onboardingController;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final ResponseFactory responseFactory = new ResponseFactory();

  private OnboardingRequest validRequest;
  private OnboardingResponse sampleResponse;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(onboardingController, "responseFactory", responseFactory);
    mockMvc =
        MockMvcBuilders.standaloneSetup(onboardingController)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    UUID bedId = UUID.randomUUID();
    validRequest =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079200001234")
            .phone("0901234567")
            .email("nguyenvana@gmail.com")
            .permanentAddress("123 Le Loi, Q1, TP.HCM")
            .dateOfBirth(LocalDate.of(2002, 5, 15))
            .emergencyContactName("Nguyen Van B")
            .emergencyContactPhone("0909876543")
            .bedId(bedId)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();

    TenantDetailResponse tenantDetail =
        TenantDetailResponse.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .tenantCode("NT-0001")
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079200001234")
            .phone("0901234567")
            .email("nguyenvana@gmail.com")
            .permanentAddress("123 Le Loi, Q1, TP.HCM")
            .dateOfBirth(LocalDate.of(2002, 5, 15))
            .emergencyContactName("Nguyen Van B")
            .emergencyContactPhone("0909876543")
            .residentStatus(ResidentStatus.ACTIVE)
            .currentBedId(bedId)
            .currentBedCode("G01")
            .currentRoomId(UUID.randomUUID())
            .currentRoomNumber("P.302")
            .vehicles(List.of())
            .contractHistory(List.of())
            .build();

    ContractDetailResponse contractDetail =
        ContractDetailResponse.builder()
            .id(UUID.randomUUID())
            .contractCode("HD-2026-0001")
            .tenantId(tenantDetail.id())
            .tenantName(tenantDetail.fullName())
            .tenantPhone(tenantDetail.phone())
            .bedId(bedId)
            .bedCode("G01")
            .roomId(tenantDetail.currentRoomId())
            .roomNumber("P.302")
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .monthlyRent(new BigDecimal("1500000"))
            .depositAmount(new BigDecimal("1500000"))
            .status(ContractStatus.ACTIVE)
            .documents(List.of())
            .build();

    GeneratedAccountInfo accountInfo =
        GeneratedAccountInfo.builder()
            .username("079200001234")
            .email("nguyenvana@gmail.com")
            .phone("0901234567")
            .initialPassword("Xy9#mK2$pQ7!")
            .roomBed("P.302 - G01")
            .build();

    OnboardingInvoiceSummary firstInvoice =
        OnboardingInvoiceSummary.builder()
            .id(UUID.randomUUID())
            .invoiceCode("INV-202609-HD-2026-0001")
            .billingPeriod(LocalDate.of(2026, 9, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 9, 5))
            .status(InvoiceStatus.DEBT)
            .build();

    sampleResponse =
        OnboardingResponse.builder()
            .tenant(tenantDetail)
            .contract(contractDetail)
            .account(accountInfo)
            .firstInvoice(firstInvoice)
            .build();
  }

  @Test
  @DisplayName(
      "POST /api/v1/admin/onboarding should return HTTP 201 with OnboardingResponse including firstInvoice")
  void onboardTenant_whenValidRequest_shouldReturn201Created() throws Exception {
    when(onboardingService.onboardTenant(any(OnboardingRequest.class))).thenReturn(sampleResponse);

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.message").value("Tiếp nhận và xếp phòng thành công"))
        .andExpect(jsonPath("$.data.tenant.tenantCode").value("NT-0001"))
        .andExpect(jsonPath("$.data.tenant.fullName").value("Nguyen Van A"))
        .andExpect(jsonPath("$.data.contract.contractCode").value("HD-2026-0001"))
        .andExpect(jsonPath("$.data.account.username").value("079200001234"))
        .andExpect(jsonPath("$.data.account.initialPassword").value("Xy9#mK2$pQ7!"))
        .andExpect(jsonPath("$.data.account.roomBed").value("P.302 - G01"))
        .andExpect(jsonPath("$.data.firstInvoice.invoiceCode").value("INV-202609-HD-2026-0001"))
        .andExpect(jsonPath("$.data.firstInvoice.amount").value(1500000))
        .andExpect(jsonPath("$.data.firstInvoice.status").value("DEBT"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/onboarding should return HTTP 400 when request validation fails")
  void onboardTenant_whenMissingRequiredFields_shouldReturn400BadRequest() throws Exception {
    OnboardingRequest invalidRequest =
        OnboardingRequest.builder()
            .fullName("")
            .idCardNumber("123")
            .email("invalid-email")
            .phone("123")
            .build();

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
  }

  static Stream<Arguments> contractErrorCodeMappings() {
    return Stream.of(
        Arguments.of(
            ContractErrorCode.ONBOARDING_ONLY_ON_DAY_ONE, 400, "CONTRACT_400_DAY_ONE_ONLY"),
        Arguments.of(ContractErrorCode.INVALID_CONTRACT_DATES, 400, "CONTRACT_400_DATES"),
        Arguments.of(ContractErrorCode.GENDER_MISMATCH, 400, "CONTRACT_400_GENDER_MISMATCH"),
        Arguments.of(ContractErrorCode.BED_NOT_AVAILABLE, 400, "CONTRACT_400_BED_UNAVAILABLE"),
        Arguments.of(ContractErrorCode.ROOM_ARCHIVED, 409, "CONTRACT_409_ROOM_ARCHIVED"),
        Arguments.of(
            ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES, 409, "CONTRACT_409_UNSETTLED_DEBT"),
        Arguments.of(
            ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT,
            409,
            "CONTRACT_409_TENANT_ACTIVE"),
        Arguments.of(ContractErrorCode.BED_ALREADY_RENTED, 409, "CONTRACT_409_BED_OCCUPIED"));
  }

  @ParameterizedTest
  @MethodSource("contractErrorCodeMappings")
  @DisplayName("POST /api/v1/admin/onboarding maps ContractErrorCode to expected status and code")
  void onboardTenant_whenContractErrorCodeThrown_shouldReturnExpectedHttpStatusAndCode(
      ContractErrorCode errorCode, int expectedHttpStatus, String expectedCode) throws Exception {
    when(onboardingService.onboardTenant(any(OnboardingRequest.class)))
        .thenThrow(new BaseAppException(errorCode));

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().is(expectedHttpStatus))
        .andExpect(jsonPath("$.httpStatus").value(expectedHttpStatus))
        .andExpect(jsonPath("$.errorCode").value(expectedCode));
  }

  @Test
  @DisplayName("POST /api/v1/admin/onboarding should return HTTP 409 when idCard is duplicated")
  void onboardTenant_whenDuplicateIdCard_shouldReturn409Conflict() throws Exception {
    when(onboardingService.onboardTenant(any(OnboardingRequest.class)))
        .thenThrow(new BaseAppException(TenantErrorCode.ID_CARD_NUMBER_DUPLICATED));

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.httpStatus").value(409))
        .andExpect(jsonPath("$.errorCode").value("TENANT_409_ID_CARD"));
  }
}
