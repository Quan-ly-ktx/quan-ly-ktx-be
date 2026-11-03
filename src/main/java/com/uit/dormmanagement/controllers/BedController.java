package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.room.UpdateBedStatusRequest;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.room.BedResponse;
import com.uit.dormmanagement.services.BedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Beds", description = "Bed management and maintenance control")
@RestController
@RequestMapping("/api/v1/admin/beds")
@RequiredArgsConstructor
public class BedController extends AbstractBaseController {

  private final BedService bedService;

  @Operation(
      summary = "Update bed status",
      description =
          "Update bed status between AVAILABLE and MAINTENANCE. Locked if bed is OCCUPIED. Requires MANAGER role.")
  @PreAuthorize("hasRole('MANAGER')")
  @PatchMapping("/{id}/status")
  public ResponseEntity<SingleResponse<BedResponse>> updateBedStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateBedStatusRequest request) {
    BedResponse response = bedService.updateBedStatus(id, request);
    return responseFactory.success(response, "Cập nhật trạng thái giường thành công");
  }
}
