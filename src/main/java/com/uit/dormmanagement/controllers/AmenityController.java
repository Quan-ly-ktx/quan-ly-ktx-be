package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.room.AmenityResponse;
import com.uit.dormmanagement.repositories.AmenityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Amenities", description = "Endpoints for managing and viewing system amenities")
@RestController
@RequestMapping("/api/v1/admin/amenities")
@RequiredArgsConstructor
public class AmenityController extends AbstractBaseController {

  private final AmenityRepository amenityRepository;

  @Operation(
      summary = "List system amenities",
      description = "Retrieve list of all available system amenities for room configuration")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping
  public ResponseEntity<SingleResponse<List<AmenityResponse>>> getAmenities() {
    List<AmenityResponse> amenities =
        amenityRepository.findAll(Sort.by("name")).stream()
            .map(a -> new AmenityResponse(a.getId(), a.getCode(), a.getName()))
            .toList();
    return success(amenities, "Lấy danh sách tiện nghi thành công");
  }
}
