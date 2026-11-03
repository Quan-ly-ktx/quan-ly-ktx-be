package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateRoomRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.base.SuccessResponse;
import com.uit.dormmanagement.dtos.response.room.RoomDetailResponse;
import com.uit.dormmanagement.dtos.response.room.RoomSummaryResponse;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.services.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Rooms",
    description = "Room and bed management endpoints for managers (FR-01 to FR-05)")
@RestController
@RequestMapping("/api/v1/admin/rooms")
@RequiredArgsConstructor
public class RoomController extends AbstractBaseController {

  private final RoomService roomService;

  @Operation(
      summary = "Get room matrix",
      description = "Retrieve room & bed matrix visual overview by floor and filters (FR-01)")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/matrix")
  public ResponseEntity<SingleResponse<List<RoomDetailResponse>>> getRoomMatrix(
      @RequestParam(required = false) Integer floor,
      @RequestParam(required = false) GenderPolicy genderPolicy,
      @RequestParam(required = false) String search) {
    List<RoomDetailResponse> response = roomService.getRoomMatrix(floor, genderPolicy, search);
    return responseFactory.success(response, "Lấy ma trận sơ đồ phòng & giường thành công");
  }

  @Operation(
      summary = "Create room",
      description = "Create a new room and auto-generate child beds (FR-02)")
  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping
  public ResponseEntity<SingleResponse<RoomDetailResponse>> createRoom(
      @Valid @RequestBody CreateRoomRequest request) {
    RoomDetailResponse response = roomService.createRoom(request);
    return responseFactory.success(HttpStatus.CREATED, response, "Thêm mới phòng thành công");
  }

  @Operation(
      summary = "Update room",
      description = "Update room basePrice, amenities, and gender policy (FR-03)")
  @PreAuthorize("hasRole('MANAGER')")
  @PutMapping("/{id}")
  public ResponseEntity<SingleResponse<RoomDetailResponse>> updateRoom(
      @PathVariable UUID id, @Valid @RequestBody UpdateRoomRequest request) {
    RoomDetailResponse response = roomService.updateRoom(id, request);
    return responseFactory.success(response, "Cập nhật thông tin phòng thành công");
  }

  @Operation(
      summary = "Delete room",
      description = "Soft delete room if vacant and no active contracts (FR-04)")
  @PreAuthorize("hasRole('MANAGER')")
  @DeleteMapping("/{id}")
  public ResponseEntity<SuccessResponse> deleteRoom(@PathVariable UUID id) {
    roomService.deleteRoom(id);
    return responseFactory.success("Xóa phòng thành công");
  }

  @Operation(
      summary = "List rooms paged",
      description = "List rooms in table view with pagination and filters (FR-05)")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping
  public ResponseEntity<SingleResponse<PageResponse<RoomSummaryResponse>>> getRooms(
      @RequestParam(required = false) Integer floor,
      @RequestParam(required = false) GenderPolicy genderPolicy,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    return responseFactory.successPage(
        roomService.getRooms(floor, genderPolicy, search, pageable),
        "Lấy danh sách phòng thành công");
  }

  @Operation(
      summary = "Get room details",
      description = "Get single room details with amenities and bed list")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<RoomDetailResponse>> getRoomDetails(@PathVariable UUID id) {
    RoomDetailResponse response = roomService.getRoomDetails(id);
    return responseFactory.success(response, "Lấy chi tiết phòng thành công");
  }
}
