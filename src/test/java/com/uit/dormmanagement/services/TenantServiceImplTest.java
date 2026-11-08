package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.tenant.UpdateTenantRequest;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantSummaryResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.impl.TenantServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private UserRepository userRepository;
  @Mock private ContractRepository contractRepository;
  @Mock private VehicleRepository vehicleRepository;

  @InjectMocks private TenantServiceImpl tenantService;

  private UUID tenantId;
  private Tenant tenant;
  private User user;
  private Room room;
  private Bed bed;
  private Contract activeContract;
  private Vehicle vehicle;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    user =
        User.builder()
            .id(UUID.randomUUID())
            .username("nam.nguyen@example.com")
            .email("nam.nguyen@example.com")
            .phone("0987654321")
            .build();

    tenant =
        Tenant.builder()
            .id(tenantId)
            .user(user)
            .tenantCode("NT-0187")
            .fullName("Nguyễn Hoàng Nam")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203001187")
            .phone("0987654321")
            .email("nam.nguyen@example.com")
            .permanentAddress("123 Lê Lợi, TP.HCM")
            .residentStatus(ResidentStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

    room = Room.builder().id(UUID.randomUUID()).roomNumber("P.302").build();
    bed = Bed.builder().id(UUID.randomUUID()).bedCode("G02").room(room).build();

    activeContract =
        Contract.builder()
            .id(UUID.randomUUID())
            .contractCode("HD-2026-0187")
            .tenant(tenant)
            .bed(bed)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 9, 1))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

    vehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
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
  @DisplayName("getTenants: should return paged summary responses with active contract details")
  void getTenants_shouldReturnPagedSummaries() {
    Pageable pageable = PageRequest.of(0, 10);
    when(tenantRepository.findActiveTenantsWithFilters(eq("Nam"), any(), any(), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(tenant), pageable, 1));
    when(contractRepository.findFirstByTenantIdAndStatusInAndDeletedAtIsNull(eq(tenantId), any()))
        .thenReturn(Optional.of(activeContract));

    Page<TenantSummaryResponse> page = tenantService.getTenants(pageable, "  Nam  ", null, null);

    assertThat(page.getTotalElements()).isEqualTo(1);
    TenantSummaryResponse summary = page.getContent().getFirst();
    assertThat(summary.tenantCode()).isEqualTo("NT-0187");
    assertThat(summary.fullName()).isEqualTo("Nguyễn Hoàng Nam");
    assertThat(summary.roomNumber()).isEqualTo("P.302");
    assertThat(summary.bedCode()).isEqualTo("G02");
    assertThat(summary.contractEndDate()).isEqualTo(LocalDate.of(2027, 9, 1));
  }

  @Test
  @DisplayName("getTenantById: when not found should throw TENANT_NOT_FOUND")
  void getTenantById_whenNotFound_shouldThrow() {
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tenantService.getTenantById(tenantId))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == TenantErrorCode.TENANT_NOT_FOUND);
  }

  @Test
  @DisplayName("getTenantById: when found should return complete detail response")
  void getTenantById_whenFound_shouldReturnDetail() {
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findFirstByTenantIdAndStatusInAndDeletedAtIsNull(eq(tenantId), any()))
        .thenReturn(Optional.of(activeContract));
    when(contractRepository.findByTenantId(tenantId)).thenReturn(List.of(activeContract));
    when(vehicleRepository.findByTenantIdAndDeletedAtIsNull(tenantId)).thenReturn(List.of(vehicle));

    TenantDetailResponse detail = tenantService.getTenantById(tenantId);

    assertThat(detail.id()).isEqualTo(tenantId);
    assertThat(detail.tenantCode()).isEqualTo("NT-0187");
    assertThat(detail.currentRoomNumber()).isEqualTo("P.302");
    assertThat(detail.currentBedCode()).isEqualTo("G02");
    assertThat(detail.currentContract()).isNotNull();
    assertThat(detail.currentContract().contractCode()).isEqualTo("HD-2026-0187");
    assertThat(detail.vehicles()).hasSize(1);
    assertThat(detail.vehicles().getFirst().licensePlate()).isEqualTo("59-X3 789.12");
    assertThat(detail.contractHistory()).hasSize(1);
  }

  @Test
  @DisplayName("updateTenant: when tenant not found should throw TENANT_NOT_FOUND")
  void updateTenant_whenNotFound_shouldThrow() {
    UpdateTenantRequest request =
        UpdateTenantRequest.builder().fullName("Nguyễn Hoàng Nam Mới").phone("0912345678").build();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tenantService.updateTenant(tenantId, request))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == TenantErrorCode.TENANT_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "updateTenant: when phone changed and already exists on another tenant should throw PHONE_DUPLICATED")
  void updateTenant_whenPhoneDuplicated_shouldThrow() {
    UpdateTenantRequest request =
        UpdateTenantRequest.builder().fullName("Nguyễn Hoàng Nam").phone("0911111111").build();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.findByPhone("0911111111"))
        .thenReturn(Optional.of(Tenant.builder().id(UUID.randomUUID()).build()));

    assertThatThrownBy(() -> tenantService.updateTenant(tenantId, request))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == TenantErrorCode.PHONE_DUPLICATED);
  }

  @Test
  @DisplayName("updateTenant: when valid should update fields on tenant and user and return detail")
  void updateTenant_whenValid_shouldUpdateAndReturnDetail() {
    UpdateTenantRequest request =
        UpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam Đã Cập Nhật")
            .phone("0912345678")
            .permanentAddress("456 Hai Bà Trưng, TP.HCM")
            .emergencyContactName("Nguyễn Văn Ba")
            .emergencyContactPhone("0988888888")
            .build();

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
    when(userRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
    when(tenantRepository.save(any(Tenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(contractRepository.findFirstByTenantIdAndStatusInAndDeletedAtIsNull(eq(tenantId), any()))
        .thenReturn(Optional.of(activeContract));
    when(contractRepository.findByTenantId(tenantId)).thenReturn(List.of(activeContract));
    when(vehicleRepository.findByTenantIdAndDeletedAtIsNull(tenantId)).thenReturn(List.of(vehicle));

    TenantDetailResponse detail = tenantService.updateTenant(tenantId, request);

    assertThat(detail.fullName()).isEqualTo("Nguyễn Hoàng Nam Đã Cập Nhật");
    assertThat(detail.phone()).isEqualTo("0912345678");
    assertThat(detail.permanentAddress()).isEqualTo("456 Hai Bà Trưng, TP.HCM");
    assertThat(detail.emergencyContactName()).isEqualTo("Nguyễn Văn Ba");
    assertThat(detail.emergencyContactPhone()).isEqualTo("0988888888");

    verify(userRepository).save(user);
    assertThat(user.getPhone()).isEqualTo("0912345678");
  }
}
