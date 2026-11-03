package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.dtos.request.room.UpdateBedStatusRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.room.BedResponse;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.services.BedService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BedControllerTest {

  @Mock private BedService bedService;

  @InjectMocks private BedController bedController;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ResponseFactory responseFactory = new ResponseFactory();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(bedController, "responseFactory", responseFactory);
    mockMvc =
        MockMvcBuilders.standaloneSetup(bedController)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();
  }

  @Test
  @DisplayName("PATCH /api/v1/admin/beds/{id}/status should return 200 when update succeeds")
  void updateBedStatus_success() throws Exception {
    UUID bedId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    BedResponse response =
        new BedResponse(
            bedId,
            roomId,
            "P.302",
            "G01",
            "P.302-G01",
            new BigDecimal("1500000.00"),
            BedStatus.MAINTENANCE);

    when(bedService.updateBedStatus(eq(bedId), any(UpdateBedStatusRequest.class)))
        .thenReturn(response);

    UpdateBedStatusRequest request = new UpdateBedStatusRequest(BedStatus.MAINTENANCE);

    mockMvc
        .perform(
            patch("/api/v1/admin/beds/{id}/status", bedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.status").value("MAINTENANCE"))
        .andExpect(jsonPath("$.data.bedCode").value("G01"));
  }

  @Test
  @DisplayName("PATCH /api/v1/admin/beds/{id}/status should return 400 when bed is occupied")
  void updateBedStatus_whenOccupied_shouldReturn400() throws Exception {
    UUID bedId = UUID.randomUUID();
    when(bedService.updateBedStatus(eq(bedId), any(UpdateBedStatusRequest.class)))
        .thenThrow(new BaseAppException(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED));

    UpdateBedStatusRequest request = new UpdateBedStatusRequest(BedStatus.MAINTENANCE);

    mockMvc
        .perform(
            patch("/api/v1/admin/beds/{id}/status", bedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("BED_400_MAINTENANCE"));
  }
}
