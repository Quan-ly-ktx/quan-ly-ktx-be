package com.uit.dormmanagement.dtos.request.tenant;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
public record TenantContactUpdateRequest(
    @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không đúng định dạng")
        String phone,
    String permanentAddress,
    String emergencyContactName,
    @Pattern(
            regexp = "^$|^(0|\\+84)[0-9]{9}$",
            message = "Số điện thoại liên hệ khẩn cấp không đúng định dạng")
        String emergencyContactPhone) {

  public boolean hasAtLeastOneField() {
    return phone != null
        || permanentAddress != null
        || emergencyContactName != null
        || emergencyContactPhone != null;
  }
}
