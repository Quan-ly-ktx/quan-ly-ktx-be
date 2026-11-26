package com.uit.dormmanagement.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.enums.GenderPolicy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantContractVehicleDtoValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  @DisplayName("OnboardingRequest should fail when required fields are missing")
  void onboardingRequest_whenMissingRequiredFields_shouldFailValidation() {
    OnboardingRequest req = OnboardingRequest.builder().build();
    Set<ConstraintViolation<OnboardingRequest>> violations = validator.validate(req);

    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("gender"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("idCardNumber"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("permanentAddress"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("bedId"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("startDate"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("endDate"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("depositAmount"));
  }

  @Test
  @DisplayName("OnboardingRequest should fail when idCardNumber is not exactly 12 digits")
  void onboardingRequest_whenInvalidCccd_shouldFailValidation() {
    OnboardingRequest req =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("123")
            .phone("0912345678")
            .email("test@example.com")
            .permanentAddress("123 Duong ABC, Quan 1, TP.HCM")
            .bedId(UUID.randomUUID())
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(6))
            .depositAmount(new BigDecimal("1000000"))
            .build();
    Set<ConstraintViolation<OnboardingRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("idCardNumber"));
  }

  @Test
  @DisplayName("OnboardingRequest should fail when phone is invalid")
  void onboardingRequest_whenInvalidPhone_shouldFailValidation() {
    OnboardingRequest req =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("012345678901")
            .phone("1234567890")
            .email("test@example.com")
            .permanentAddress("123 Duong ABC, Quan 1, TP.HCM")
            .bedId(UUID.randomUUID())
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(6))
            .depositAmount(new BigDecimal("1000000"))
            .build();
    Set<ConstraintViolation<OnboardingRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
  }

  @Test
  @DisplayName("OnboardingRequest should fail when email is invalid")
  void onboardingRequest_whenInvalidEmail_shouldFailValidation() {
    OnboardingRequest req =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("012345678901")
            .phone("0912345678")
            .email("invalid-email")
            .permanentAddress("123 Duong ABC, Quan 1, TP.HCM")
            .bedId(UUID.randomUUID())
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(6))
            .depositAmount(new BigDecimal("1000000"))
            .build();
    Set<ConstraintViolation<OnboardingRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
  }

  @Test
  @DisplayName("OnboardingRequest should fail when depositAmount is negative")
  void onboardingRequest_whenNegativeDeposit_shouldFailValidation() {
    OnboardingRequest req =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("012345678901")
            .phone("0912345678")
            .email("test@example.com")
            .permanentAddress("123 Duong ABC, Quan 1, TP.HCM")
            .bedId(UUID.randomUUID())
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(6))
            .depositAmount(new BigDecimal("-100000"))
            .build();
    Set<ConstraintViolation<OnboardingRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("depositAmount"));
  }

  @Test
  @DisplayName("OnboardingRequest should pass when all fields are valid")
  void onboardingRequest_whenValid_shouldPassValidation() {
    OnboardingRequest req =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("012345678901")
            .phone("0912345678")
            .email("test@example.com")
            .permanentAddress("123 Duong ABC, Quan 1, TP.HCM")
            .dateOfBirth(LocalDate.of(2002, 5, 15))
            .emergencyContactName("Nguyen Van B")
            .emergencyContactPhone("0987654321")
            .bedId(UUID.randomUUID())
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(6))
            .depositAmount(new BigDecimal("1000000"))
            .build();
    Set<ConstraintViolation<OnboardingRequest>> violations = validator.validate(req);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("CreateVehicleAdminRequest should fail when required fields missing")
  void createVehicleAdminRequest_whenMissingFields_shouldFailValidation() {
    CreateVehicleAdminRequest req = CreateVehicleAdminRequest.builder().build();
    Set<ConstraintViolation<CreateVehicleAdminRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("tenantId"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("vehicleType"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("brand"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("color"));
  }

  @Test
  @DisplayName("IssueCardRequest should fail when cardCode is blank")
  void issueCardRequest_whenBlankCardCode_shouldFailValidation() {
    IssueCardRequest req = new IssueCardRequest("   ");
    Set<ConstraintViolation<IssueCardRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cardCode"));
  }

  @Test
  @DisplayName("TenantRegisterVehicleRequest should fail when required fields missing")
  void tenantRegisterVehicleRequest_whenMissingFields_shouldFailValidation() {
    TenantRegisterVehicleRequest req = TenantRegisterVehicleRequest.builder().build();
    Set<ConstraintViolation<TenantRegisterVehicleRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("vehicleType"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("brand"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("color"));
  }

  @Test
  @DisplayName("RenewContractRequest should fail when newEndDate is null")
  void renewContractRequest_whenNullEndDate_shouldFailValidation() {
    RenewContractRequest req = new RenewContractRequest(null);
    Set<ConstraintViolation<RenewContractRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newEndDate"));
  }

  @Test
  @DisplayName("ManagerUpdateTenantRequest should pass when all fields are valid")
  void managerUpdateTenantRequest_whenValid_shouldPassValidation() {
    com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest req =
        com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Văn A")
            .idCardNumber("123456789012")
            .gender(GenderPolicy.MALE)
            .email("test@example.com")
            .phone("0912345678")
            .permanentAddress("123 Hà Nội")
            .dateOfBirth(LocalDate.of(2000, 1, 1))
            .emergencyContactName("Bố A")
            .emergencyContactPhone("0987654321")
            .build();
    Set<ConstraintViolation<com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest>>
        violations = validator.validate(req);
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName(
      "ManagerUpdateTenantRequest should fail when fullName is blank or idCardNumber has wrong format")
  void managerUpdateTenantRequest_whenInvalid_shouldFailValidation() {
    com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest req =
        com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest.builder()
            .fullName("")
            .idCardNumber("1234")
            .phone("0912345678")
            .permanentAddress("123 Hà Nội")
            .build();
    Set<ConstraintViolation<com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest>>
        violations = validator.validate(req);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("idCardNumber"));
  }

  @Test
  @DisplayName("TenantContactUpdateRequest should pass when phone is valid")
  void tenantContactUpdateRequest_whenValid_shouldPassValidation() {
    com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest req =
        com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest.builder()
            .phone("0912345678")
            .permanentAddress("456 Đà Nẵng")
            .build();
    Set<ConstraintViolation<com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest>>
        violations = validator.validate(req);
    assertThat(violations).isEmpty();
    assertThat(req.hasAtLeastOneField()).isTrue();
  }

  @Test
  @DisplayName("TenantContactUpdateRequest should fail when phone format is invalid")
  void tenantContactUpdateRequest_whenInvalidPhone_shouldFailValidation() {
    com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest req =
        com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest.builder()
            .phone("123")
            .build();
    Set<ConstraintViolation<com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest>>
        violations = validator.validate(req);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
  }

  @Test
  @DisplayName(
      "ManagerUpdateTenantRequest retains null optional identity fields and rejects supplied blanks")
  void managerUpdateTenantRequest_optionalIdentityBlankVsNull() {
    var nullIdentityReq =
        com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Văn A")
            .idCardNumber(null)
            .email(null)
            .phone("0912345678")
            .permanentAddress("123 Hà Nội")
            .emergencyContactName("   ")
            .emergencyContactPhone("   ")
            .build();
    assertThat(validator.validate(nullIdentityReq)).isEmpty();

    var blankIdentityReq =
        com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Văn A")
            .idCardNumber("   ")
            .email("")
            .phone("0912345678")
            .permanentAddress("123 Hà Nội")
            .build();
    var blankViolations = validator.validate(blankIdentityReq);
    assertThat(blankViolations)
        .anyMatch(v -> v.getPropertyPath().toString().equals("idCardNumber"));
    assertThat(blankViolations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));

    var whitespaceEmailReq =
        com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Văn A")
            .email("   ")
            .phone("0912345678")
            .permanentAddress("123 Hà Nội")
            .build();
    assertThat(validator.validate(whitespaceEmailReq))
        .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
  }

  @Test
  @DisplayName(
      "TenantContactUpdateRequest rejects blank phone/address and permits blank emergency fields")
  void tenantContactUpdateRequest_blankValidation() {
    var blankRequiredReq =
        com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest.builder()
            .phone("")
            .permanentAddress("   ")
            .build();
    var violations = validator.validate(blankRequiredReq);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("permanentAddress"));

    var blankOptionalClearReq =
        com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest.builder()
            .emergencyContactName("")
            .emergencyContactPhone("   ")
            .build();
    assertThat(validator.validate(blankOptionalClearReq)).isEmpty();
  }
}
