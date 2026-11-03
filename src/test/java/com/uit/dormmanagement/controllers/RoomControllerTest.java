package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateRoomRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.room.RoomDetailResponse;
import com.uit.dormmanagement.dtos.response.room.RoomSummaryResponse;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.services.RoomService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

  @Mock private RoomService roomService;

  @InjectMocks private RoomController roomController;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ResponseFactory responseFactory = new ResponseFactory();

  private UUID roomId;
  private RoomDetailResponse sampleDetail;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(roomController, "responseFactory", responseFactory);
    mockMvc =
        MockMvcBuilders.standaloneSetup(roomController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    roomId = UUID.randomUUID();
    sampleDetail =
        new RoomDetailResponse(
            roomId,
            "P.302",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            4,
            0,
            new BigDecimal("1500000.00"),
            List.of(),
            List.of());
  }

  @Test
  @DisplayName("GET /api/v1/admin/rooms/matrix should return HTTP 200 with list of room details")
  void getRoomMatrix_success() throws Exception {
    when(roomService.getRoomMatrix(3, GenderPolicy.MALE, "302")).thenReturn(List.of(sampleDetail));

    mockMvc
        .perform(
            get("/api/v1/admin/rooms/matrix")
                .param("floor", "3")
                .param("genderPolicy", "MALE")
                .param("search", "302"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data[0].roomNumber").value("P.302"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/rooms should return HTTP 201 when request is valid")
  void createRoom_success() throws Exception {
    CreateRoomRequest request =
        new CreateRoomRequest(
            "P.302",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            new BigDecimal("1500000.00"),
            List.of());

    when(roomService.createRoom(any(CreateRoomRequest.class))).thenReturn(sampleDetail);

    mockMvc
        .perform(
            post("/api/v1/admin/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.data.roomNumber").value("P.302"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/rooms should return HTTP 400 when roomNumber is invalid")
  void createRoom_invalidNumber_shouldReturn400() throws Exception {
    CreateRoomRequest request =
        new CreateRoomRequest(
            "INVALID",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            new BigDecimal("1500000.00"),
            List.of());

    mockMvc
        .perform(
            post("/api/v1/admin/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
  }

  @Test
  @DisplayName("PUT /api/v1/admin/rooms/{id} should return HTTP 200 when updated")
  void updateRoom_success() throws Exception {
    UpdateRoomRequest request =
        new UpdateRoomRequest(GenderPolicy.FEMALE, new BigDecimal("1700000.00"), List.of());

    when(roomService.updateRoom(eq(roomId), any(UpdateRoomRequest.class))).thenReturn(sampleDetail);

    mockMvc
        .perform(
            put("/api/v1/admin/rooms/{id}", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.roomNumber").value("P.302"));
  }

  @Test
  @DisplayName("DELETE /api/v1/admin/rooms/{id} should return HTTP 200 with SuccessResponse")
  void deleteRoom_success() throws Exception {
    doNothing().when(roomService).deleteRoom(roomId);

    mockMvc
        .perform(delete("/api/v1/admin/rooms/{id}", roomId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200));
  }

  @Test
  @DisplayName("GET /api/v1/admin/rooms should return HTTP 200 with PageResponse")
  void getRooms_paged_success() throws Exception {
    RoomSummaryResponse summary =
        new RoomSummaryResponse(
            roomId,
            "P.302",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            4,
            0,
            new BigDecimal("1500000.00"),
            List.of());

    when(roomService.getRooms(eq(null), eq(null), eq(null), any()))
        .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

    mockMvc
        .perform(get("/api/v1/admin/rooms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.items[0].roomNumber").value("P.302"));
  }

  @Test
  @DisplayName("GET /api/v1/admin/rooms/{id} should return HTTP 404 when not found")
  void getRoomDetails_notFound() throws Exception {
    when(roomService.getRoomDetails(roomId))
        .thenThrow(new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/admin/rooms/{id}", roomId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ROOM_404"));
  }
}
