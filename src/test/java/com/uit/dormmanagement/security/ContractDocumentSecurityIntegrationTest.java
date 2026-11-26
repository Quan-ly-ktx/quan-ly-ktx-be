package com.uit.dormmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractDocument;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractDocumentRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.ContractService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContractDocumentSecurityIntegrationTest {

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractDocumentRepository contractDocumentRepository;
  @Autowired private ContractService contractService;
  @Autowired private InMemoryPrivateObjectStorage privateObjectStorage;
  @Autowired private UserTokenInvalidationService userTokenInvalidationService;
  @Autowired private TransactionTemplate transactionTemplate;

  private User managerUser;
  private User activeTenantUser;
  private User otherTenantUser;
  private User checkedOutTenantUser;
  private User technicianUser;

  private Tenant activeTenant;
  private Tenant checkedOutTenant;
  private Room room;
  private Bed activeBed;
  private Bed otherBed;
  private Bed checkedOutBed;

  private Contract activeContract;
  private Contract otherContract;
  private Contract liquidatedContract;

  private ContractDocument activeDoc;
  private ContractDocument otherDoc;
  private ContractDocument checkedOutDoc;

  private byte[] activeDocBytes;
  private byte[] checkedOutDocBytes;
  private String activeDocKey;
  private String checkedOutDocKey;

  private String tokenFor(User user) {
    String gen = userTokenInvalidationService.getOrCreateGeneration(user.getId());
    return jwtService.generateAccessToken(user, UUID.randomUUID().toString(), gen);
  }

  private void setUpFixtures() {
    managerUser =
        userRepository.save(
            User.builder()
                .email("cdoc_manager@example.com")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    staffProfileRepository.save(
        StaffProfile.builder()
            .user(managerUser)
            .fullName("Manager Cdoc")
            .phone("0988002211")
            .build());

    technicianUser =
        userRepository.save(
            User.builder()
                .email("cdoc_tech@example.com")
                .passwordHash("hashed")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());

    activeTenantUser =
        userRepository.save(
            User.builder()
                .email("cdoc_tenant1@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    activeTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(activeTenantUser)
                .tenantCode("NT-CDOC-01")
                .fullName("Active Tenant")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079204111111")
                .phone("0911223301")
                .permanentAddress("Address 1")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    otherTenantUser =
        userRepository.save(
            User.builder()
                .email("cdoc_tenant2@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant otherTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(otherTenantUser)
                .tenantCode("NT-CDOC-02")
                .fullName("Other Tenant")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079204222222")
                .phone("0911223302")
                .permanentAddress("Address 2")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    checkedOutTenantUser =
        userRepository.save(
            User.builder()
                .email("cdoc_tenant3@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    checkedOutTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(checkedOutTenantUser)
                .tenantCode("NT-CDOC-03")
                .fullName("Checked Out Tenant")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079204333333")
                .phone("0911223303")
                .permanentAddress("Address 3")
                .residentStatus(ResidentStatus.CHECKED_OUT)
                .build());

    room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.801")
                .floor(8)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds((short) 4)
                .basePrice(new BigDecimal("1500000"))
                .build());

    activeBed =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G01")
                .slotKey("P.801-G01")
                .status(BedStatus.OCCUPIED)
                .build());
    otherBed =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G02")
                .slotKey("P.801-G02")
                .status(BedStatus.OCCUPIED)
                .build());
    checkedOutBed =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G03")
                .slotKey("P.801-G03")
                .status(BedStatus.AVAILABLE)
                .build());

    activeContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-CDOC-01")
                .tenant(activeTenant)
                .bed(activeBed)
                .roomCodeSnapshot("P.801")
                .bedCodeSnapshot("G01")
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 9, 1))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    otherContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-CDOC-02")
                .tenant(otherTenant)
                .bed(otherBed)
                .roomCodeSnapshot("P.801")
                .bedCodeSnapshot("G02")
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 9, 1))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    liquidatedContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-CDOC-03")
                .tenant(checkedOutTenant)
                .bed(checkedOutBed)
                .roomCodeSnapshot("P.801")
                .bedCodeSnapshot("G03")
                .startOn(LocalDate.of(2025, 9, 1))
                .originalEndOn(LocalDate.of(2026, 8, 31))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.LIQUIDATED)
                .build());

    activeDocBytes = "%PDF-1.4 active contract bytes".getBytes(StandardCharsets.UTF_8);
    activeDocKey =
        "contracts/" + activeContract.getId() + "/" + UUID.randomUUID() + "_hop_dong.pdf";
    privateObjectStorage.putObject(activeDocKey, "application/pdf", activeDocBytes);
    activeDoc =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(activeContract)
                .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .originalFilename("Hợp đồng thuê.pdf")
                .mimeType("application/pdf")
                .storageFileKey(activeDocKey)
                .build());
    activeContract.getDocuments().add(activeDoc);

    String otherDocKey =
        "contracts/" + otherContract.getId() + "/" + UUID.randomUUID() + "_other.pdf";
    privateObjectStorage.putObject(
        otherDocKey, "application/pdf", "other".getBytes(StandardCharsets.UTF_8));
    otherDoc =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(otherContract)
                .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .originalFilename("other.pdf")
                .mimeType("application/pdf")
                .storageFileKey(otherDocKey)
                .build());

    checkedOutDocBytes = "%PDF-1.4 checkout handover".getBytes(StandardCharsets.UTF_8);
    checkedOutDocKey =
        "contracts/" + liquidatedContract.getId() + "/" + UUID.randomUUID() + "_handover.pdf";
    privateObjectStorage.putObject(checkedOutDocKey, "application/pdf", checkedOutDocBytes);
    checkedOutDoc =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(liquidatedContract)
                .documentType(ContractDocumentCategory.CHECKOUT_HANDOVER)
                .originalFilename("handover.pdf")
                .mimeType("application/pdf")
                .storageFileKey(checkedOutDocKey)
                .build());
  }

  @Test
  @DisplayName("Manager can stream any contract document content with safe inline headers")
  void adminStreamDocument_shouldReturnBytesAndHeaders() throws Exception {
    setUpFixtures();
    String managerToken = tokenFor(managerUser);

    mockMvc
        .perform(
            get(
                    "/api/v1/admin/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    activeDoc.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
        .andExpect(
            header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(activeDocBytes.length)))
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Hop_dong_thue.pdf\""))
        .andExpect(content().bytes(activeDocBytes));
  }

  @Test
  @DisplayName("Owning ACTIVE and CHECKED_OUT tenants can stream their own contract documents")
  void tenantStreamOwnDocument_activeAndCheckedOutAllowed() throws Exception {
    setUpFixtures();
    String activeToken = tokenFor(activeTenantUser);
    String checkedOutToken = tokenFor(checkedOutTenantUser);

    mockMvc
        .perform(
            get(
                    "/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    activeDoc.getId())
                .header("Authorization", "Bearer " + activeToken))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
        .andExpect(
            header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(activeDocBytes.length)))
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Hop_dong_thue.pdf\""))
        .andExpect(content().bytes(activeDocBytes));

    mockMvc
        .perform(
            get(
                    "/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content",
                    liquidatedContract.getId(),
                    checkedOutDoc.getId())
                .header("Authorization", "Bearer " + checkedOutToken))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
        .andExpect(
            header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(checkedOutDocBytes.length)))
        .andExpect(
            header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"handover.pdf\""))
        .andExpect(content().bytes(checkedOutDocBytes));
  }

  @Test
  @DisplayName("Unauthorized actors, cross-tenant, and cross-contract document access fail closed")
  void documentStreaming_enforcesAuthRbacOwnershipAndContractBinding() throws Exception {
    setUpFixtures();
    String activeToken = tokenFor(activeTenantUser);
    String otherToken = tokenFor(otherTenantUser);
    String managerToken = tokenFor(managerUser);
    String techToken = tokenFor(technicianUser);

    // Anonymous -> 401
    mockMvc
        .perform(
            get(
                "/api/v1/admin/contracts/{contractId}/documents/{documentId}/content",
                activeContract.getId(),
                activeDoc.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get(
                "/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content",
                activeContract.getId(),
                activeDoc.getId()))
        .andExpect(status().isUnauthorized());

    // Technician -> 403
    mockMvc
        .perform(
            get(
                    "/api/v1/admin/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    activeDoc.getId())
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get(
                    "/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    activeDoc.getId())
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());

    // Cross-role -> 403
    mockMvc
        .perform(
            get(
                    "/api/v1/admin/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    activeDoc.getId())
                .header("Authorization", "Bearer " + activeToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get(
                    "/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    activeDoc.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    // Non-owning tenant -> 404 (discloses nothing)
    mockMvc
        .perform(
            get(
                    "/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    activeDoc.getId())
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());

    // Cross-contract documentId -> 404 for both admin and tenant
    mockMvc
        .perform(
            get(
                    "/api/v1/admin/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    otherDoc.getId())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get(
                    "/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content",
                    activeContract.getId(),
                    otherDoc.getId())
                .header("Authorization", "Bearer " + activeToken))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Legacy /uploads/** paths are denied for anonymous and authenticated callers")
  void legacyUploads_allPathsDenied() throws Exception {
    setUpFixtures();
    String managerToken = tokenFor(managerUser);

    for (String path :
        new String[] {
          "/uploads/contracts/test.pdf", "/uploads/vehicles/test.jpg", "/uploads/any.txt"
        }) {
      mockMvc
          .perform(get(path))
          .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
      mockMvc
          .perform(get(path).header("Authorization", "Bearer " + managerToken))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName(
      "Contract and Tenant detail responses expose authorized streaming URLs and never raw keys")
  void contractDocumentResponse_exposesStreamingUrlNeverRawKeyOrUploads() throws Exception {
    setUpFixtures();
    String managerToken = tokenFor(managerUser);
    String activeToken = tokenFor(activeTenantUser);

    String expectedAdminUrl =
        "/api/v1/admin/contracts/"
            + activeContract.getId()
            + "/documents/"
            + activeDoc.getId()
            + "/content";
    String expectedTenantUrl =
        "/api/v1/tenant/contracts/"
            + activeContract.getId()
            + "/documents/"
            + activeDoc.getId()
            + "/content";

    String adminContractJson =
        mockMvc
            .perform(
                get("/api/v1/admin/contracts/{id}", activeContract.getId())
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.documents[0].fileUrl").value(expectedAdminUrl))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(adminContractJson).doesNotContain(activeDocKey).doesNotContain("/uploads/");

    String tenantContractJson =
        mockMvc
            .perform(
                get("/api/v1/tenant/contracts/me").header("Authorization", "Bearer " + activeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.documents[0].fileUrl").value(expectedTenantUrl))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(tenantContractJson).doesNotContain(activeDocKey).doesNotContain("/uploads/");

    String adminTenantJson =
        mockMvc
            .perform(
                get("/api/v1/admin/tenants/{id}", activeTenant.getId())
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.currentContract.documents[0].fileUrl").value(expectedAdminUrl))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(adminTenantJson).doesNotContain(activeDocKey).doesNotContain("/uploads/");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName(
      "deleteDocument keeps object on transaction rollback and deletes object only after commit")
  void deleteDocument_transactionCommitAndRollbackBehavior() {
    User committedUser =
        userRepository.save(
            User.builder()
                .email("cdoc_tx_user@example.com")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant committedTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(committedUser)
                .tenantCode("NT-CDOC-TX")
                .fullName("Tx Tenant")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079204999888")
                .phone("0911223399")
                .permanentAddress("Tx Address")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());
    Room committedRoom =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.899")
                .floor(8)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds((short) 4)
                .basePrice(new BigDecimal("1500000"))
                .build());
    Bed committedBed =
        bedRepository.save(
            Bed.builder()
                .room(committedRoom)
                .bedCode("G01")
                .slotKey("P.899-G01")
                .status(BedStatus.OCCUPIED)
                .build());
    Contract committedContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-CDOC-TX")
                .tenant(committedTenant)
                .bed(committedBed)
                .roomCodeSnapshot("P.899")
                .bedCodeSnapshot("G01")
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 9, 1))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    String txKey = "contracts/" + committedContract.getId() + "/" + UUID.randomUUID() + "_tx.pdf";
    privateObjectStorage.putObject(
        txKey, "application/pdf", "tx-bytes".getBytes(StandardCharsets.UTF_8));
    ContractDocument committedDoc =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(committedContract)
                .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .originalFilename("tx.pdf")
                .mimeType("application/pdf")
                .storageFileKey(txKey)
                .build());

    try {
      // 1. Rollback after deleteDocument -> object must still exist
      transactionTemplate.executeWithoutResult(
          status -> {
            contractService.deleteDocument(committedContract.getId(), committedDoc.getId());
            assertThat(privateObjectStorage.exists(txKey)).isTrue();
            status.setRollbackOnly();
          });
      assertThat(privateObjectStorage.exists(txKey)).isTrue();
      assertThat(contractDocumentRepository.findById(committedDoc.getId())).isPresent();

      // 2. Commit after deleteDocument -> object must be deleted after commit
      transactionTemplate.executeWithoutResult(
          status ->
              contractService.deleteDocument(committedContract.getId(), committedDoc.getId()));
      assertThat(privateObjectStorage.exists(txKey)).isFalse();
      assertThat(contractDocumentRepository.findById(committedDoc.getId())).isEmpty();
    } finally {
      privateObjectStorage.deleteObject(txKey);
      contractDocumentRepository
          .findById(committedDoc.getId())
          .ifPresent(contractDocumentRepository::delete);
      contractRepository.deleteById(committedContract.getId());
      bedRepository.deleteById(committedBed.getId());
      roomRepository.deleteById(committedRoom.getId());
      tenantRepository.deleteById(committedTenant.getId());
      userRepository.deleteById(committedUser.getId());
    }
  }
}
