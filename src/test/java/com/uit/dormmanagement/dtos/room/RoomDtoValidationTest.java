package com.uit.dormmanagement.dtos.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.room.RoomAmenityRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateBedStatusRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateRoomRequest;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomDtoValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  @DisplayName("CreateRoomRequest should fail when required fields are missing")
  void createRoomRequest_whenBlank_shouldFail() {
    CreateRoomRequest request = new CreateRoomRequest(null, null, null, null, null, null);
    Set<ConstraintViolation<CreateRoomRequest>> violations = validator.validate(request);

    assertThat(violations).hasSize(5);
  }

  @Test
  @DisplayName("CreateRoomRequest should fail when roomNumber has invalid pattern")
  void createRoomRequest_invalidPattern_shouldFail() {
    CreateRoomRequest request =
        new CreateRoomRequest(
            "101",
            1,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            new BigDecimal("1500000.00"),
            List.of());
    Set<ConstraintViolation<CreateRoomRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("roomNumber"));
  }

  @Test
  @DisplayName("CreateRoomRequest should pass when valid")
  void createRoomRequest_whenValid_shouldPass() {
    CreateRoomRequest request =
        new CreateRoomRequest(
            "P.302",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            new BigDecimal("1500000.00"),
            List.of(new RoomAmenityRequest(UUID.randomUUID(), 2)));
    Set<ConstraintViolation<CreateRoomRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("UpdateRoomRequest should fail when basePrice is negative")
  void updateRoomRequest_negativePrice_shouldFail() {
    UpdateRoomRequest request =
        new UpdateRoomRequest(GenderPolicy.FEMALE, new BigDecimal("-100.00"), List.of());
    Set<ConstraintViolation<UpdateRoomRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("basePrice"));
  }

  @Test
  @DisplayName("UpdateBedStatusRequest should fail when status is null")
  void updateBedStatusRequest_nullStatus_shouldFail() {
    UpdateBedStatusRequest request = new UpdateBedStatusRequest(null);
    Set<ConstraintViolation<UpdateBedStatusRequest>> violations = validator.validate(request);

    assertThat(violations).hasSize(1);
  }
}
