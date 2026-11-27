package com.uit.dormmanagement.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import java.util.Arrays;
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
    assertThat(ContractErrorCode.CONTRACT_EXPIRED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ContractErrorCode.CONTRACT_EXPIRED.getErrorCode()).isEqualTo("CONTRACT_409_EXPIRED");
    assertThat(ContractErrorCode.CONTRACT_RENEWAL_CONFLICT.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(ContractErrorCode.CONTRACT_RENEWAL_CONFLICT.getErrorCode())
        .isEqualTo("CONTRACT_409_RENEWAL_CONFLICT");
    assertThat(ContractErrorCode.DOCUMENT_NOT_FOUND.getHttpStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ContractErrorCode.DOCUMENT_FILE_SIZE_EXCEEDED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.INVALID_CHECKOUT_BED_STATUS.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.INVALID_CHECKOUT_BED_STATUS.getErrorCode())
        .isEqualTo("CONTRACT_400_CHECKOUT_BED_STATUS");
    assertThat(ContractErrorCode.CHECKOUT_HANDOVER_NOT_CONFIRMED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ContractErrorCode.CHECKOUT_HANDOVER_NOT_CONFIRMED.getErrorCode())
        .isEqualTo("CONTRACT_400_HANDOVER_REQUIRED");

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

  @Test
  @DisplayName("invoiceErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields")
  void invoiceErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields() {
    assertThat(InvoiceErrorCode.values()).hasSize(13);

    assertThat(InvoiceErrorCode.INVOICE_NOT_FOUND.getErrorCode()).isEqualTo("INVOICE_404");
    assertThat(InvoiceErrorCode.INVOICE_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(InvoiceErrorCode.INVOICE_PROOF_NOT_FOUND.getErrorCode())
        .isEqualTo("INVOICE_404_PROOF");
    assertThat(InvoiceErrorCode.INVOICE_PROOF_NOT_FOUND.getHttpStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);

    assertThat(InvoiceErrorCode.INVALID_BILLING_PERIOD.getErrorCode())
        .isEqualTo("INVOICE_400_INVALID_PERIOD");
    assertThat(InvoiceErrorCode.INVALID_BILLING_PERIOD.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(InvoiceErrorCode.FUTURE_BILLING_PERIOD_NOT_ALLOWED.getErrorCode())
        .isEqualTo("INVOICE_400_FUTURE_PERIOD");
    assertThat(InvoiceErrorCode.FUTURE_BILLING_PERIOD_NOT_ALLOWED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(InvoiceErrorCode.ISSUANCE_WINDOW_NOT_OPEN.getErrorCode())
        .isEqualTo("INVOICE_400_ISSUANCE_WINDOW");
    assertThat(InvoiceErrorCode.ISSUANCE_WINDOW_NOT_OPEN.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(InvoiceErrorCode.PROOF_FILE_REQUIRED.getErrorCode())
        .isEqualTo("INVOICE_400_PROOF_REQUIRED");
    assertThat(InvoiceErrorCode.PROOF_FILE_REQUIRED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(InvoiceErrorCode.PROOF_FILE_SIZE_EXCEEDED.getErrorCode())
        .isEqualTo("INVOICE_400_PROOF_SIZE");
    assertThat(InvoiceErrorCode.PROOF_FILE_SIZE_EXCEEDED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT.getErrorCode())
        .isEqualTo("INVOICE_400_PROOF_FORMAT");
    assertThat(InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED.getErrorCode())
        .isEqualTo("INVOICE_403_STAFF_PROFILE");
    assertThat(InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED.getHttpStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(InvoiceErrorCode.INVOICE_ALREADY_PAID.getErrorCode())
        .isEqualTo("INVOICE_409_ALREADY_PAID");
    assertThat(InvoiceErrorCode.INVOICE_ALREADY_PAID.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(InvoiceErrorCode.INVOICE_NOT_DEBT.getErrorCode()).isEqualTo("INVOICE_409_NOT_DEBT");
    assertThat(InvoiceErrorCode.INVOICE_NOT_DEBT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION.getErrorCode())
        .isEqualTo("INVOICE_409_NOT_PENDING");
    assertThat(InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(InvoiceErrorCode.DUPLICATE_INVOICE_PERIOD.getErrorCode())
        .isEqualTo("INVOICE_409_DUPLICATE_PERIOD");
    assertThat(InvoiceErrorCode.DUPLICATE_INVOICE_PERIOD.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);

    for (InvoiceErrorCode code : InvoiceErrorCode.values()) {
      assertThat(code.getErrorCode()).isNotBlank().startsWith("INVOICE_");
      assertThat(code.getMessage()).isNotBlank();
      assertThat(code.getHttpStatus()).isNotNull();
    }
  }

  @Test
  @DisplayName("expenseErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields")
  void expenseErrorCode_shouldHaveAllRequiredCodesAndNonBlankFields() {
    assertThat(ExpenseErrorCode.values()).hasSize(10);

    assertThat(ExpenseErrorCode.EXPENSE_NOT_FOUND.getErrorCode()).isEqualTo("EXPENSE_404");
    assertThat(ExpenseErrorCode.EXPENSE_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND.getErrorCode())
        .isEqualTo("EXPENSE_404_RECEIPT");
    assertThat(ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND.getHttpStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);

    assertThat(ExpenseErrorCode.INVALID_REPORT_PERIOD.getErrorCode())
        .isEqualTo("EXPENSE_400_INVALID_PERIOD");
    assertThat(ExpenseErrorCode.INVALID_REPORT_PERIOD.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ExpenseErrorCode.INVALID_EXPENSE_AMOUNT.getErrorCode())
        .isEqualTo("EXPENSE_400_AMOUNT");
    assertThat(ExpenseErrorCode.INVALID_EXPENSE_AMOUNT.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ExpenseErrorCode.INVALID_EXPORT_FORMAT.getErrorCode())
        .isEqualTo("EXPENSE_400_EXPORT_FORMAT");
    assertThat(ExpenseErrorCode.INVALID_EXPORT_FORMAT.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ExpenseErrorCode.RECEIPT_FILE_REQUIRED.getErrorCode())
        .isEqualTo("EXPENSE_400_RECEIPT_REQUIRED");
    assertThat(ExpenseErrorCode.RECEIPT_FILE_REQUIRED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ExpenseErrorCode.RECEIPT_FILE_SIZE_EXCEEDED.getErrorCode())
        .isEqualTo("EXPENSE_400_RECEIPT_SIZE");
    assertThat(ExpenseErrorCode.RECEIPT_FILE_SIZE_EXCEEDED.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT.getErrorCode())
        .isEqualTo("EXPENSE_400_RECEIPT_FORMAT");
    assertThat(ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT.getHttpStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(ExpenseErrorCode.MANAGER_STAFF_PROFILE_REQUIRED.getErrorCode())
        .isEqualTo("EXPENSE_403_STAFF_PROFILE");
    assertThat(ExpenseErrorCode.MANAGER_STAFF_PROFILE_REQUIRED.getHttpStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS.getErrorCode())
        .isEqualTo("EXPENSE_409_SALARY_MONTH");
    assertThat(ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS.getHttpStatus())
        .isEqualTo(HttpStatus.CONFLICT);

    assertThat(
            Arrays.stream(ExpenseErrorCode.values()).map(ExpenseErrorCode::getErrorCode).toList())
        .doesNotHaveDuplicates();

    for (ExpenseErrorCode code : ExpenseErrorCode.values()) {
      assertThat(code.getErrorCode()).isNotBlank().startsWith("EXPENSE_");
      assertThat(code.getMessage()).isNotBlank();
      assertThat(code.getHttpStatus()).isNotNull();
    }
  }
}
