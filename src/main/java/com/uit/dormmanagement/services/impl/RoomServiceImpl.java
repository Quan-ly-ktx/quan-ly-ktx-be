package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.room.RoomAmenityRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateRoomRequest;
import com.uit.dormmanagement.dtos.response.room.AmenityItemResponse;
import com.uit.dormmanagement.dtos.response.room.BedSlotResponse;
import com.uit.dormmanagement.dtos.response.room.OccupantSummaryResponse;
import com.uit.dormmanagement.dtos.response.room.RoomDetailResponse;
import com.uit.dormmanagement.dtos.response.room.RoomSummaryResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.RoomAmenity;
import com.uit.dormmanagement.entities.RoomAmenityId;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.repositories.AmenityRepository;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomAmenityRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.services.RoomService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

  private final RoomRepository roomRepository;
  private final BedRepository bedRepository;
  private final RoomAmenityRepository roomAmenityRepository;
  private final AmenityRepository amenityRepository;
  private final ContractRepository contractRepository;

  @Override
  @Transactional(readOnly = true)
  public List<RoomDetailResponse> getRoomMatrix(
      Integer floor, GenderPolicy genderPolicy, String search) {
    List<Room> rooms = roomRepository.findRoomsWithFilters(floor, genderPolicy, search);
    if (rooms.isEmpty()) {
      return Collections.emptyList();
    }

    List<UUID> roomIds = rooms.stream().map(Room::getId).toList();
    List<Bed> allBeds =
        roomIds.stream()
            .flatMap(rId -> bedRepository.findByRoomIdOrderByBedCodeAsc(rId).stream())
            .toList();

    List<UUID> bedIds = allBeds.stream().map(Bed::getId).toList();
    Map<UUID, Contract> contractByBedId =
        bedIds.isEmpty()
            ? Collections.emptyMap()
            : contractRepository
                .findByBedIdInAndStatusInAndDeletedAtIsNull(
                    bedIds, Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON))
                .stream()
                .collect(
                    Collectors.toMap(c -> c.getBed().getId(), Function.identity(), (c1, c2) -> c1));

    Map<UUID, List<Bed>> bedsByRoomId =
        allBeds.stream().collect(Collectors.groupingBy(b -> b.getRoom().getId()));

    return rooms.stream()
        .map(
            room -> {
              List<Bed> roomBeds = bedsByRoomId.getOrDefault(room.getId(), Collections.emptyList());
              List<BedSlotResponse> bedSlots =
                  roomBeds.stream()
                      .map(bed -> toBedSlotResponse(bed, contractByBedId.get(bed.getId())))
                      .toList();
              List<AmenityItemResponse> amenities = getAmenityItems(room.getId());
              return toRoomDetailResponse(room, amenities, bedSlots);
            })
        .toList();
  }

  @Override
  @Transactional
  public RoomDetailResponse createRoom(CreateRoomRequest request) {
    if (roomRepository.existsByRoomNumber(request.roomNumber())) {
      throw new BaseAppException(RoomErrorCode.ROOM_NUMBER_DUPLICATED);
    }

    int totalBeds = request.roomType().getBedCount();

    Room room =
        Room.builder()
            .roomNumber(request.roomNumber())
            .floor(request.floor())
            .roomType(request.roomType())
            .genderPolicy(request.genderPolicy())
            .totalBeds(totalBeds)
            .occupiedBeds(0)
            .basePrice(request.basePrice())
            .build();

    Room savedRoom = roomRepository.save(room);

    List<Bed> beds = new ArrayList<>(totalBeds);
    for (int i = 1; i <= totalBeds; i++) {
      String bedCode = String.format("G%02d", i);
      String slotKey = savedRoom.getRoomNumber() + "-" + bedCode;
      beds.add(
          Bed.builder()
              .room(savedRoom)
              .bedCode(bedCode)
              .slotKey(slotKey)
              .price(savedRoom.getBasePrice())
              .status(BedStatus.AVAILABLE)
              .build());
    }
    bedRepository.saveAll(beds);

    if (request.amenities() != null && !request.amenities().isEmpty()) {
      for (RoomAmenityRequest item : request.amenities()) {
        amenityRepository
            .findById(item.amenityId())
            .ifPresent(
                amenity ->
                    roomAmenityRepository.save(
                        RoomAmenity.builder()
                            .id(new RoomAmenityId(savedRoom.getId(), amenity.getId()))
                            .room(savedRoom)
                            .amenity(amenity)
                            .quantity(item.quantity())
                            .build()));
      }
    }

    List<BedSlotResponse> bedSlots =
        beds.stream().map(bed -> toBedSlotResponse(bed, null)).toList();
    List<AmenityItemResponse> amenities = getAmenityItems(savedRoom.getId());

    return toRoomDetailResponse(savedRoom, amenities, bedSlots);
  }

  @Override
  @Transactional
  public RoomDetailResponse updateRoom(UUID roomId, UpdateRoomRequest request) {
    Room room =
        roomRepository
            .findById(roomId)
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND));

    if (request.genderPolicy() != null && request.genderPolicy() != room.getGenderPolicy()) {
      if (room.getOccupiedBeds() > 0) {
        throw new BaseAppException(RoomErrorCode.CANNOT_CHANGE_GENDER_POLICY);
      }
      room.setGenderPolicy(request.genderPolicy());
    }

    if (request.basePrice() != null) {
      room.setBasePrice(request.basePrice());
      List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(roomId);
      for (Bed bed : beds) {
        bed.setPrice(request.basePrice());
      }
      bedRepository.saveAll(beds);
    }

    if (request.amenities() != null) {
      roomAmenityRepository.deleteByRoomId(roomId);
      for (RoomAmenityRequest item : request.amenities()) {
        amenityRepository
            .findById(item.amenityId())
            .ifPresent(
                amenity ->
                    roomAmenityRepository.save(
                        RoomAmenity.builder()
                            .id(new RoomAmenityId(roomId, amenity.getId()))
                            .room(room)
                            .amenity(amenity)
                            .quantity(item.quantity())
                            .build()));
      }
    }

    Room savedRoom = roomRepository.save(room);

    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(roomId);
    List<UUID> bedIds = beds.stream().map(Bed::getId).toList();
    Map<UUID, Contract> contractByBedId =
        bedIds.isEmpty()
            ? Collections.emptyMap()
            : contractRepository
                .findByBedIdInAndStatusInAndDeletedAtIsNull(
                    bedIds, Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON))
                .stream()
                .collect(
                    Collectors.toMap(c -> c.getBed().getId(), Function.identity(), (c1, c2) -> c1));

    List<BedSlotResponse> bedSlots =
        beds.stream().map(bed -> toBedSlotResponse(bed, contractByBedId.get(bed.getId()))).toList();
    List<AmenityItemResponse> amenities = getAmenityItems(savedRoom.getId());

    return toRoomDetailResponse(savedRoom, amenities, bedSlots);
  }

  @Override
  @Transactional
  public void deleteRoom(UUID roomId) {
    Room room =
        roomRepository
            .findById(roomId)
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND));

    if (room.getOccupiedBeds() > 0) {
      throw new BaseAppException(RoomErrorCode.CANNOT_DELETE_OCCUPIED_ROOM);
    }

    boolean hasActiveContracts =
        contractRepository.existsByBed_Room_IdAndStatusInAndDeletedAtIsNull(
            roomId, Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON));
    if (hasActiveContracts) {
      throw new BaseAppException(RoomErrorCode.CANNOT_DELETE_OCCUPIED_ROOM);
    }

    room.setDeletedAt(Instant.now());
    roomRepository.save(room);
  }

  @Override
  @Transactional(readOnly = true)
  public RoomDetailResponse getRoomDetails(UUID roomId) {
    Room room =
        roomRepository
            .findById(roomId)
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND));

    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(roomId);
    List<UUID> bedIds = beds.stream().map(Bed::getId).toList();
    Map<UUID, Contract> contractByBedId =
        bedIds.isEmpty()
            ? Collections.emptyMap()
            : contractRepository
                .findByBedIdInAndStatusInAndDeletedAtIsNull(
                    bedIds, Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON))
                .stream()
                .collect(
                    Collectors.toMap(c -> c.getBed().getId(), Function.identity(), (c1, c2) -> c1));

    List<BedSlotResponse> bedSlots =
        beds.stream().map(bed -> toBedSlotResponse(bed, contractByBedId.get(bed.getId()))).toList();
    List<AmenityItemResponse> amenities = getAmenityItems(room.getId());

    return toRoomDetailResponse(room, amenities, bedSlots);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<RoomSummaryResponse> getRooms(
      Integer floor, GenderPolicy genderPolicy, String search, Pageable pageable) {
    Page<Room> pagedRooms = roomRepository.findRoomsPaged(floor, genderPolicy, search, pageable);

    return pagedRooms.map(
        room ->
            new RoomSummaryResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getFloor(),
                room.getRoomType(),
                room.getGenderPolicy(),
                room.getTotalBeds(),
                room.getOccupiedBeds(),
                room.getBasePrice(),
                getAmenityItems(room.getId())));
  }

  private List<AmenityItemResponse> getAmenityItems(UUID roomId) {
    List<RoomAmenity> roomAmenities = roomAmenityRepository.findByRoomId(roomId);
    return roomAmenities.stream()
        .map(
            ra ->
                new AmenityItemResponse(
                    ra.getAmenity().getId(),
                    ra.getAmenity().getCode(),
                    ra.getAmenity().getName(),
                    ra.getAmenity().getCategory(),
                    ra.getQuantity()))
        .toList();
  }

  private BedSlotResponse toBedSlotResponse(Bed bed, Contract activeContract) {
    OccupantSummaryResponse occupant = null;
    if (activeContract != null && activeContract.getTenant() != null) {
      Tenant tenant = activeContract.getTenant();
      occupant =
          new OccupantSummaryResponse(
              tenant.getId(), tenant.getFullName(), tenant.getPhone(), activeContract.getEndDate());
    }

    return new BedSlotResponse(
        bed.getId(), bed.getBedCode(), bed.getSlotKey(), bed.getPrice(), bed.getStatus(), occupant);
  }

  private RoomDetailResponse toRoomDetailResponse(
      Room room, List<AmenityItemResponse> amenities, List<BedSlotResponse> beds) {
    return new RoomDetailResponse(
        room.getId(),
        room.getRoomNumber(),
        room.getFloor(),
        room.getRoomType(),
        room.getGenderPolicy(),
        room.getTotalBeds(),
        room.getOccupiedBeds(),
        room.getBasePrice(),
        amenities,
        beds);
  }
}
