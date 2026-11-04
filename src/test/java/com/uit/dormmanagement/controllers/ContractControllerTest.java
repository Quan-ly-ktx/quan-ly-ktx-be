package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.ContractService;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
class ContractControllerTest {

  @Mock private ContractService contractService;

  @InjectMocks private AdminContractController adminContractController;
  @InjectMocks private TenantContractController tenantContractController;

  private MockMvc adminMockMvc;
  private MockMvc tenantMockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final ResponseFactory responseFactory = new ResponseFactory();

  private UUID contractId;
  private UUID tenantId;
  private UUID userId;
  private ContractSummaryResponse sampleSummary;
  private ContractDetailResponse sampleDetail;
  private UserPrincipal userPrincipal;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(adminContractController, "responseFactory", responseFactory);
    ReflectionTestUtils.setField(tenantContractController, "responseFactory", responseFactory);

    contractId = UUID.randomUUID();
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
        MockMvcBuilders.standaloneSetup(adminContractController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    tenantMockMvc =
        MockMvcBuilders.standaloneSetup(tenantContractController)
            .setCustomArgumentResolvers(principalResolver)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    sampleSummary =
        ContractSummaryResponse.builder()
            .id(contractId)
            .contractCode("HD-2026-0187")
            .tenantId(tenantId)
            .tenantName("Nguyễn Hoàng Nam")
            .roomNumber("P.302")
            .bedCode("G02")
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 9, 1))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .build();

    sampleDetail =
        ContractDetailResponse.builder()
            .id(contractId)
            .contractCode("HD-2026-0187")
            .tenantId(tenantId)
            .tenantName("Nguyễn Hoàng Nam")
            .roomNumber("P.302")
            .bedCode("G02")
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 9, 1))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .documents(List.of())
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/contracts: should return paged contracts")
  void getContracts_shouldReturnPaged() throws Exception {
    Page<ContractSummaryResponse> page =
        new PageImpl<>(List.of(sampleSummary), PageRequest.of(0, 10), 1);
    when(contractService.getContracts(any(), any(), any())).thenReturn(page);

    adminMockMvc
        .perform(get("/api/v1/admin/contracts").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.items[0].contractCode").value("HD-2026-0187"));
  }

  @Test
  @DisplayName("GET /api/v1/admin/contracts/{id}: should return contract detail")
  void getContractById_shouldReturnDetail() throws Exception {
    when(contractService.getContractById(contractId)).thenReturn(sampleDetail);

    adminMockMvc
        .perform(get("/api/v1/admin/contracts/{id}", contractId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.contractCode").value("HD-2026-0187"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/contracts/{id}/terminate: Checkout should return 200")
  void terminateContract_shouldReturn200() throws Exception {
    ContractDetailResponse terminated =
        ContractDetailResponse.builder().id(contractId).status(ContractStatus.TERMINATED).build();
    when(contractService.terminateContract(contractId)).thenReturn(terminated);

    adminMockMvc
        .perform(post("/api/v1/admin/contracts/{id}/terminate", contractId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.status").value("TERMINATED"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/contracts/{id}/renew: should renew and return 200")
  void renewContract_shouldReturn200() throws Exception {
    ContractDetailResponse renewed =
        ContractDetailResponse.builder()
            .id(contractId)
            .endDate(LocalDate.of(2028, 9, 1))
            .status(ContractStatus.ACTIVE)
            .build();

    when(contractService.renewContract(eq(contractId), any(), any())).thenReturn(renewed);

    adminMockMvc
        .perform(
            multipart("/api/v1/admin/contracts/{id}/renew", contractId)
                .param("newEndDate", "2028-09-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/contracts/{id}/documents: should upload multi documents")
  void uploadDocuments_shouldReturn200() throws Exception {
    MockMultipartFile docFile =
        new MockMultipartFile("files", "contract.pdf", "application/pdf", new byte[] {1, 2});

    ContractDocumentResponse docRes =
        ContractDocumentResponse.builder()
            .id(UUID.randomUUID())
            .fileName("contract.pdf")
            .fileUrl("/uploads/contracts/contract.pdf")
            .documentCategory(ContractDocumentCategory.ORIGINAL_CONTRACT)
            .build();

    when(contractService.uploadDocuments(
            eq(contractId), eq(ContractDocumentCategory.ORIGINAL_CONTRACT), any()))
        .thenReturn(List.of(docRes));

    adminMockMvc
        .perform(
            multipart("/api/v1/admin/contracts/{id}/documents", contractId)
                .file(docFile)
                .param("category", "ORIGINAL_CONTRACT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data[0].fileName").value("contract.pdf"));
  }

  @Test
  @DisplayName("DELETE /api/v1/admin/contracts/{id}/documents/{documentId}: should delete document")
  void deleteDocument_shouldReturn200() throws Exception {
    UUID docId = UUID.randomUUID();

    adminMockMvc
        .perform(delete("/api/v1/admin/contracts/{id}/documents/{documentId}", contractId, docId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200));

    verify(contractService).deleteDocument(contractId, docId);
  }

  @Test
  @DisplayName("GET /api/v1/tenant/contracts/me: should return logged-in tenant contract")
  void getMyContract_shouldReturnContract() throws Exception {
    when(contractService.getMyContract(userId)).thenReturn(sampleDetail);

    tenantMockMvc
        .perform(get("/api/v1/tenant/contracts/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.contractCode").value("HD-2026-0187"));
  }
}
