package com.uit.dormmanagement.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.base.ErrorResponse;
import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class AppExceptionHandlerDataIntegrityTest {

  private AppExceptionHandler handler;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new AppExceptionHandler(new ResponseFactory());
    request = new MockHttpServletRequest("POST", "/api/v1/test");
  }

  static Stream<Arguments> knownUniqueConstraints() {
    return Stream.of(
        Arguments.of("tenants_id_card_number_key", TenantErrorCode.ID_CARD_NUMBER_DUPLICATED),
        Arguments.of(
            "public.\"TENANTS_ID_CARD_NUMBER_KEY\"", TenantErrorCode.ID_CARD_NUMBER_DUPLICATED),
        Arguments.of("uq_users_email_canonical", AuthErrorCode.DUPLICATE_EMAIL),
        Arguments.of("uq_rooms_number_canonical", RoomErrorCode.ROOM_NUMBER_DUPLICATED),
        Arguments.of("room_code_registry_pkey", RoomErrorCode.ROOM_NUMBER_DUPLICATED),
        Arguments.of("uq_beds_slot_key_canonical", RoomErrorCode.ROOM_NUMBER_DUPLICATED),
        Arguments.of("uq_contracts_active_bed", ContractErrorCode.BED_ALREADY_RENTED),
        Arguments.of(
            "uq_contracts_active_tenant", ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT),
        Arguments.of("uq_vehicles_issued_card_code", VehicleErrorCode.CARD_CODE_DUPLICATED),
        Arguments.of("uq_vehicles_tenant_active_quota", VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED),
        Arguments.of(
            "uq_vehicle_change_requests_pending_tenant",
            VehicleErrorCode.VEHICLE_CHANGE_REQUEST_ALREADY_EXISTS));
  }

  @ParameterizedTest
  @MethodSource("knownUniqueConstraints")
  @DisplayName("Known unique constraint violations map to expected domain 409 error codes")
  void knownUniqueConstraints_shouldMapToDomain409(
      String constraintName, AppErrorCode expectedCode) {
    SQLException sqlEx = new SQLException("duplicate key value", "23505");
    ConstraintViolationException hibernateCve =
        new ConstraintViolationException("could not execute statement", sqlEx, constraintName);
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException("integrity error", hibernateCve);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(dive, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(409);
    assertThat(response.getBody().getErrorCode()).isEqualTo(expectedCode.getErrorCode());
  }

  @Test
  @DisplayName("Unmapped unique violation (SQLSTATE 23505) maps to 409 WEB_409")
  void unmappedUniqueViolation_shouldReturn409Conflict() {
    SQLException sqlEx =
        new SQLException(
            "duplicate key value violates unique constraint \"uq_invoices_contract_period\"",
            "23505");
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException("could not execute statement", sqlEx);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(dive, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(409);
    assertThat(response.getBody().getErrorCode()).isEqualTo(WebErrorCode.CONFLICT.getErrorCode());
  }

  @Test
  @DisplayName(
      "PostgreSQL PSQLException-style getServerErrorMessage().getConstraint() is supported")
  void postgresServerErrorMessageReflection_shouldExtractConstraintName() {
    SQLException fakePgEx = new FakePgSqlException("23505", "uq_vehicles_issued_card_code");
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException("could not execute statement", fakePgEx);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(dive, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode())
        .isEqualTo(VehicleErrorCode.CARD_CODE_DUPLICATED.getErrorCode());
  }

  static Stream<Arguments> nonUniquenessIntegrityCases() {
    return Stream.of(
        Arguments.of("NOT NULL 23502", "23502", "null value in column full_name"),
        Arguments.of("CHECK 23514", "23514", "ck_vehicles_motor_registration"),
        Arguments.of("FK 23503", "23503", "fk_contracts_bed"),
        Arguments.of("Data truncation 22001", "22001", null),
        Arguments.of("Invalid text format 22P02", "22P02", null));
  }

  @ParameterizedTest
  @MethodSource("nonUniquenessIntegrityCases")
  @DisplayName("Non-uniqueness integrity violations remain HTTP 400 WEB_400")
  void nonUniquenessIntegrityViolations_shouldReturn400(
      String label, String sqlState, String constraintName) {
    SQLException sqlEx = new SQLException(label + " failure", sqlState);
    Throwable cause =
        constraintName != null
            ? new ConstraintViolationException("constraint failure", sqlEx, constraintName)
            : sqlEx;
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException("integrity error", cause);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(dive, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(400);
    assertThat(response.getBody().getErrorCode())
        .isEqualTo(WebErrorCode.BAD_REQUEST.getErrorCode());
  }

  @Test
  @DisplayName(
      "Unclassified DataIntegrityViolationException without SQLSTATE remains HTTP 400 WEB_400")
  void unclassifiedDataIntegrityViolation_shouldReturn400() {
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException("generic integrity failure");

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(dive, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(400);
    assertThat(response.getBody().getErrorCode())
        .isEqualTo(WebErrorCode.BAD_REQUEST.getErrorCode());
  }

  @Test
  @DisplayName("handleDataIntegrityViolation logs metadata without raw PostgreSQL detail values")
  void handleDataIntegrityViolation_shouldNotLogRawSensitiveDetail() {
    String rawSensitiveCccd = "079203001187";
    SQLException sqlEx =
        new SQLException(
            "ERROR: duplicate key value violates unique constraint \"tenants_id_card_number_key\"\n"
                + "Detail: Key (id_card_number)=("
                + rawSensitiveCccd
                + ") already exists.",
            "23505");
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException(sqlEx.getMessage(), sqlEx);

    Logger logger = (Logger) LoggerFactory.getLogger(AppExceptionHandler.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.WARN);

    try {
      ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(dive, request);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

      List<String> messages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).noneMatch(msg -> msg.contains(rawSensitiveCccd));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @SuppressWarnings("unused")
  private static final class FakeServerErrorMessage {
    private final String constraint;

    private FakeServerErrorMessage(String constraint) {
      this.constraint = constraint;
    }

    public String getConstraint() {
      return constraint;
    }
  }

  @SuppressWarnings("unused")
  private static final class FakePgSqlException extends SQLException {
    private final FakeServerErrorMessage serverErrorMessage;

    private FakePgSqlException(String sqlState, String constraint) {
      super("pg error", sqlState);
      this.serverErrorMessage = new FakeServerErrorMessage(constraint);
    }

    public FakeServerErrorMessage getServerErrorMessage() {
      return serverErrorMessage;
    }
  }
}
