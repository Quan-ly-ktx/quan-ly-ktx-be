package com.uit.dormmanagement.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DomainErrorCodeTest {

  @Test
  @DisplayName("errorCodes_shouldHaveProperHttpStatusAndCodePrefix")
  void errorCodes_shouldHaveProperHttpStatusAndCodePrefix() {
    assertThat(TenantErrorCode.TENANT_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ContractErrorCode.GENDER_MISMATCH.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(WebErrorCode.CONFLICT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(WebErrorCode.CONFLICT.getErrorCode()).isEqualTo("WEB_409");
  }

  @Test
  @DisplayName("tenantErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields")
  void tenantErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields() {
    assertThat(TenantErrorCode.TENANT_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(TenantErrorCode.ID_CARD_NUMBER_DUPLICATED.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(TenantErrorCode.EMAIL_DUPLICATED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(TenantErrorCode.PHONE_DUPLICATED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(TenantErrorCode.INVALID_ID_CARD_FORMAT.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(TenantErrorCode.TENANT_NOT_ACTIVE.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

    for (TenantErrorCode code : TenantErrorCode.values()) {
      assertThat(code.getErrorCode()).isNotBlank().startsWith("TENANT_");
      assertThat(code.getMessage()).isNotBlank();
      assertThat(code.getHttpStatus()).isNotNull();
    }
  }

  @Test
  @DisplayName("contractErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields")
  void contractErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields() {
    assertThat(ContractErrorCode.CONTRACT_NOT_FOUND.getHttpStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ContractErrorCode.CONTRACT_ALREADY_TERMINATED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.BED_NOT_AVAILABLE.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.GENDER_MISMATCH.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.INVALID_CONTRACT_DATES.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.ONBOARDING_ONLY_ON_DAY_ONE.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.ONBOARDING_ONLY_ON_DAY_ONE.getErrorCode())
        .isEqualTo("CONTRACT_400_DAY_ONE_ONLY");
    assertThat(ContractErrorCode.INVALID_DEPOSIT_AMOUNT.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.INVALID_DEPOSIT_AMOUNT.getErrorCode())
        .isEqualTo("CONTRACT_400_DEPOSIT");
    assertThat(ContractErrorCode.BED_ALREADY_RENTED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ContractErrorCode.BED_ALREADY_RENTED.getErrorCode())
        .isEqualTo("CONTRACT_409_BED_OCCUPIED");
    assertThat(ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT.getErrorCode())
        .isEqualTo("CONTRACT_409_TENANT_ACTIVE");
    assertThat(ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES.getErrorCode())
        .isEqualTo("CONTRACT_409_UNSETTLED_DEBT");
    assertThat(ContractErrorCode.ROOM_ARCHIVED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ContractErrorCode.ROOM_ARCHIVED.getErrorCode())
        .isEqualTo("CONTRACT_409_ROOM_ARCHIVED");
    assertThat(ContractErrorCode.DOCUMENT_NOT_FOUND.getHttpStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ContractErrorCode.DOCUMENT_FILE_SIZE_EXCEEDED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    for (ContractErrorCode code : ContractErrorCode.values()) {
      assertThat(code.getErrorCode()).isNotBlank().startsWith("CONTRACT_");
      assertThat(code.getMessage()).isNotBlank();
      assertThat(code.getHttpStatus()).isNotNull();
    }
  }

  @Test
  @DisplayName("vehicleErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields")
  void vehicleErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields() {
    assertThat(VehicleErrorCode.VEHICLE_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(VehicleErrorCode.LICENSE_PLATE_DUPLICATED.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(VehicleErrorCode.CARD_CODE_DUPLICATED.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(VehicleErrorCode.VEHICLE_ALREADY_ACTIVE.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(VehicleErrorCode.VEHICLE_NOT_PENDING.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);

    for (VehicleErrorCode code : VehicleErrorCode.values()) {
      assertThat(code.getErrorCode()).isNotBlank().startsWith("VEHICLE_");
      assertThat(code.getMessage()).isNotBlank();
      assertThat(code.getHttpStatus()).isNotNull();
    }
  }
}
