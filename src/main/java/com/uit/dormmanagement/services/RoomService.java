package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateRoomRequest;
import com.uit.dormmanagement.dtos.response.room.RoomDetailResponse;
import com.uit.dormmanagement.dtos.response.room.RoomSummaryResponse;
import com.uit.dormmanagement.enums.GenderPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoomService {
  List<RoomDetailResponse> getRoomMatrix(Integer floor, GenderPolicy genderPolicy, String search);

  RoomDetailResponse createRoom(CreateRoomRequest request);

  RoomDetailResponse updateRoom(UUID roomId, UpdateRoomRequest request);

  void deleteRoom(UUID roomId);

  RoomDetailResponse getRoomDetails(UUID roomId);

  Page<RoomSummaryResponse> getRooms(
      Integer floor, GenderPolicy genderPolicy, String search, Pageable pageable);
}
