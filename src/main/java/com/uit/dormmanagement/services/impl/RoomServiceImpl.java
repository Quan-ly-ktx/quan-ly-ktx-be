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
import com.uit.dormmanagement.entities.RoomCodeRegistry;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.repositories.AmenityRepository;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomAmenityRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
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
  private final RoomCodeRegistryRepository roomCodeRegistryRepository;
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
                .findByBedIdInAndStatusIn(
                    bedIds, Set.of(ContractStatus.ACTIVE))
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
    String roomNumber = canonicalRoomNumber(request.roomNumber());
    if (roomRepository.existsByRoomNumber(roomNumber)
        || roomCodeRegistryRepository.existsById(roomNumber)) {
      throw new BaseAppException(RoomErrorCode.ROOM_NUMBER_DUPLICATED);
    }

    int totalBeds = request.roomType().getBedCount();

    Room room =
        Room.builder()
            .roomNumber(roomNumber)
            .floor(request.floor())
            .genderPolicy(request.genderPolicy())
            .totalBeds((short) totalBeds)
            .basePrice(request.basePrice())
            .build();

    Room savedRoom = roomRepository.save(room);
    roomCodeRegistryRepository.save(
        RoomCodeRegistry.builder().code(roomNumber).room(savedRoom).current(true).build());

    List<Bed> beds = new ArrayList<>(totalBeds);
    for (int i = 1; i <= totalBeds; i++) {
      String bedCode = String.format("G%02d", i);
      String slotKey = savedRoom.getRoomNumber() + "-" + bedCode;
      beds.add(
          Bed.builder()
              .room(savedRoom)
              .bedCode(bedCode)
              .slotKey(slotKey.toUpperCase())
              .status(BedStatus.AVAILABLE)
              .build());
    }
    bedRepository.saveAll(beds);

    if (request.amenities() != null && !request.amenities().isEmpty()) {
      attachAmenities(savedRoom, request.amenities());
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

    if (request.roomNumber() != null && !request.roomNumber().isBlank()) {
      renameRoom(room, canonicalRoomNumber(request.roomNumber()));
    }

    if (request.genderPolicy() != null && request.genderPolicy() != room.getGenderPolicy()) {
      if (bedRepository.countByRoomIdAndStatus(roomId, BedStatus.OCCUPIED) > 0) {
        throw new BaseAppException(RoomErrorCode.CANNOT_CHANGE_GENDER_POLICY);
      }
      room.setGenderPolicy(request.genderPolicy());
    }

    if (request.basePrice() != null) {
      room.setBasePrice(request.basePrice());
    }

    if (request.amenities() != null) {
      roomAmenityRepository.deleteByRoomId(roomId);
      attachAmenities(room, request.amenities());
    }

    Room savedRoom = roomRepository.save(room);

    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(roomId);
    List<UUID> bedIds = beds.stream().map(Bed::getId).toList();
    Map<UUID, Contract> contractByBedId =
        bedIds.isEmpty()
            ? Collections.emptyMap()
            : contractRepository
                .findByBedIdInAndStatusIn(
                    bedIds, Set.of(ContractStatus.ACTIVE))
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

    if (bedRepository.countByRoomIdAndStatus(roomId, BedStatus.OCCUPIED) > 0) {
      throw new BaseAppException(RoomErrorCode.CANNOT_DELETE_OCCUPIED_ROOM);
    }

    boolean hasActiveContracts =
        contractRepository.existsByBed_Room_IdAndStatusIn(
            roomId, Set.of(ContractStatus.ACTIVE));
    if (hasActiveContracts) {
      throw new BaseAppException(RoomErrorCode.CANNOT_DELETE_OCCUPIED_ROOM);
    }

    room.setArchivedAt(Instant.now());
    roomRepository.save(room);
  }

  @Override
  @Transactional
  public void restoreRoom(UUID roomId) {
    int restored = roomRepository.restoreArchivedRoom(roomId, Instant.now());
    if (restored == 0) {
      throw new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND);
    }
  }

  @Override
  @Transactional
  public void deleteIncorrectRoom(UUID roomId) {
    Room room =
        roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND));
    if (roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(roomId)) {
      throw new BaseAppException(RoomErrorCode.ROOM_CODE_PROTECTED);
    }
    roomAmenityRepository.deleteByRoomId(roomId);
    bedRepository.deleteAll(bedRepository.findByRoomIdOrderByBedCodeAsc(roomId));
    roomCodeRegistryRepository.deleteUnusedCodes(roomId);
    roomRepository.delete(room);
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
                .findByBedIdInAndStatusIn(
                    bedIds, Set.of(ContractStatus.ACTIVE))
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
                roomTypeFor(room),
                room.getGenderPolicy(),
                room.getTotalBeds(),
                occupiedBedCount(room.getId()),
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
        bed.getId(), bed.getBedCode(), bed.getSlotKey(), bed.getRoom().getBasePrice(), bed.getStatus(), occupant);
  }

  private RoomDetailResponse toRoomDetailResponse(
      Room room, List<AmenityItemResponse> amenities, List<BedSlotResponse> beds) {
    return new RoomDetailResponse(
        room.getId(),
        room.getRoomNumber(),
        room.getFloor(),
        roomTypeFor(room),
        room.getGenderPolicy(),
        room.getTotalBeds(),
        occupiedBedCount(room.getId()),
        room.getBasePrice(),
        amenities,
        beds);
  }

  private void attachAmenities(Room room, List<RoomAmenityRequest> requested) {
    Set<UUID> requestedIds = requested.stream().map(RoomAmenityRequest::amenityId).collect(Collectors.toSet());
    List<com.uit.dormmanagement.entities.Amenity> found = amenityRepository.findAllById(requestedIds);
    if (found.size() != requestedIds.size()) {
      throw new BaseAppException(RoomErrorCode.AMENITY_NOT_FOUND);
    }
    Map<UUID, com.uit.dormmanagement.entities.Amenity> amenityById =
        found.stream().collect(Collectors.toMap(com.uit.dormmanagement.entities.Amenity::getId, Function.identity()));
    for (RoomAmenityRequest item : requested) {
      com.uit.dormmanagement.entities.Amenity amenity = amenityById.get(item.amenityId());
      roomAmenityRepository.save(
          RoomAmenity.builder()
              .id(new RoomAmenityId(room.getId(), amenity.getId()))
              .room(room)
              .amenity(amenity)
              .quantity(item.quantity())
              .build());
    }
  }

  private void renameRoom(Room room, String newCode) {
    String oldCode = room.getRoomNumber();
    if (oldCode.equals(newCode)) {
      return;
    }
    if (roomRepository.existsByRoomNumber(newCode)) {
      throw new BaseAppException(RoomErrorCode.ROOM_NUMBER_DUPLICATED);
    }
    RoomCodeRegistry existingCode = roomCodeRegistryRepository.findById(newCode).orElse(null);
    if (existingCode != null) {
      throw new BaseAppException(
          existingCode.isUsedInBusiness()
              ? RoomErrorCode.ROOM_CODE_PROTECTED
              : RoomErrorCode.ROOM_NUMBER_DUPLICATED);
    }

    RoomCodeRegistry currentCode =
        roomCodeRegistryRepository.findByRoom_IdAndCurrentTrue(room.getId())
            .orElseThrow(() -> new IllegalStateException("Room has no current registry code"));
    currentCode.setCurrent(false);
    if (currentCode.isUsedInBusiness()) {
      roomCodeRegistryRepository.saveAndFlush(currentCode);
    } else {
      roomCodeRegistryRepository.delete(currentCode);
      roomCodeRegistryRepository.flush();
    }
    roomCodeRegistryRepository.save(
        RoomCodeRegistry.builder().code(newCode).room(room).current(true).build());

    room.setRoomNumber(newCode);
    List<Bed> roomBeds = bedRepository.findByRoomIdOrderByBedCodeAsc(room.getId());
    roomBeds.forEach(bed -> bed.setSlotKey(newCode + "-" + bed.getBedCode()));
    bedRepository.saveAll(roomBeds);
  }

  private int occupiedBedCount(UUID roomId) {
    return Math.toIntExact(bedRepository.countByRoomIdAndStatus(roomId, BedStatus.OCCUPIED));
  }

  private RoomType roomTypeFor(Room room) {
    return switch (room.getTotalBeds()) {
      case 4 -> RoomType.FOUR_BEDS;
      case 6 -> RoomType.SIX_BEDS;
      case 8 -> RoomType.EIGHT_BEDS;
      default -> throw new IllegalStateException("Unsupported room capacity: " + room.getTotalBeds());
    };
  }

  private String canonicalRoomNumber(String roomNumber) {
    return roomNumber.trim().toUpperCase();
  }
}
