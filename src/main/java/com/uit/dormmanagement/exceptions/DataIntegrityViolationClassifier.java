package com.uit.dormmanagement.exceptions;

import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Classifies {@link DataIntegrityViolationException} root causes using SQLSTATE and database
 * constraint/index names so unique conflicts map to HTTP 409 while non-uniqueness integrity errors
 * (CHECK, NOT NULL, generic FK, data truncation) remain HTTP 400.
 */
public final class DataIntegrityViolationClassifier {

  public static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
  public static final String FOREIGN_KEY_VIOLATION_SQL_STATE = "23503";

  private static final Pattern PG_CONSTRAINT_PATTERN =
      Pattern.compile("constraint\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

  private static final Map<String, AppErrorCode> GLOBAL_UNIQUE_CONSTRAINT_MAP =
      Map.ofEntries(
          Map.entry("tenants_id_card_number_key", TenantErrorCode.ID_CARD_NUMBER_DUPLICATED),
          Map.entry("uq_users_email_canonical", AuthErrorCode.DUPLICATE_EMAIL),
          Map.entry("uq_rooms_number_canonical", RoomErrorCode.ROOM_NUMBER_DUPLICATED),
          Map.entry("room_code_registry_pkey", RoomErrorCode.ROOM_NUMBER_DUPLICATED),
          Map.entry("uq_beds_slot_key_canonical", RoomErrorCode.ROOM_NUMBER_DUPLICATED),
          Map.entry("uq_contracts_active_bed", ContractErrorCode.BED_ALREADY_RENTED),
          Map.entry(
              "uq_contracts_active_tenant", ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT),
          Map.entry("uq_contract_renewals_sequence", ContractErrorCode.CONTRACT_RENEWAL_CONFLICT),
          Map.entry("uq_contract_renewals_effective", ContractErrorCode.CONTRACT_RENEWAL_CONFLICT),
          Map.entry("uq_vehicles_issued_card_code", VehicleErrorCode.CARD_CODE_DUPLICATED),
          Map.entry("uq_vehicles_tenant_active_quota", VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED),
          Map.entry(
              "uq_vehicle_change_requests_pending_tenant",
              VehicleErrorCode.VEHICLE_CHANGE_REQUEST_ALREADY_EXISTS),
          Map.entry("uq_invoices_contract_period", InvoiceErrorCode.DUPLICATE_INVOICE_PERIOD),
          Map.entry("invoices_invoice_code_key", InvoiceErrorCode.DUPLICATE_INVOICE_PERIOD),
          Map.entry("payment_transactions_invoice_id_key", InvoiceErrorCode.INVOICE_ALREADY_PAID),
          Map.entry("payment_transactions_receipt_code_key", InvoiceErrorCode.INVOICE_ALREADY_PAID),
          Map.entry(
              "uq_facility_expenses_salary_month", ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS));

  private DataIntegrityViolationClassifier() {}

  public record ViolationInfo(String constraintName, String sqlState) {}

  /** Walks the cause and {@link SQLException#getNextException()} chain to extract SQL metadata. */
  public static ViolationInfo extract(Throwable throwable) {
    if (throwable == null) {
      return new ViolationInfo(null, null);
    }

    String constraintName = null;
    String sqlState = null;
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    Throwable current = throwable;
    while (current != null && visited.add(current) && visited.size() <= 25) {
      if (current instanceof ConstraintViolationException cve) {
        if (constraintName == null && hasText(cve.getConstraintName())) {
          constraintName = normalizeConstraintName(cve.getConstraintName());
        }
        if (sqlState == null && hasText(cve.getSQLState())) {
          sqlState = cve.getSQLState().trim();
        }
        SQLException nestedSql = cve.getSQLException();
        if (nestedSql != null) {
          ViolationInfo sqlInfo = extractFromSqlExceptionChain(nestedSql, visited);
          if (constraintName == null && sqlInfo.constraintName() != null) {
            constraintName = sqlInfo.constraintName();
          }
          if (sqlState == null && sqlInfo.sqlState() != null) {
            sqlState = sqlInfo.sqlState();
          }
        }
      }

      if (current instanceof SQLException sqlEx) {
        ViolationInfo sqlInfo = extractFromSqlExceptionChain(sqlEx, visited);
        if (constraintName == null && sqlInfo.constraintName() != null) {
          constraintName = sqlInfo.constraintName();
        }
        if (sqlState == null && sqlInfo.sqlState() != null) {
          sqlState = sqlInfo.sqlState();
        }
      }

      current = current.getCause();
    }

    if (constraintName == null) {
      constraintName = extractConstraintFromMessages(throwable, sqlState);
    }

    return new ViolationInfo(constraintName, sqlState);
  }

  public static boolean isUniqueViolation(Throwable throwable) {
    return UNIQUE_VIOLATION_SQL_STATE.equals(extract(throwable).sqlState());
  }

  public static boolean isForeignKeyViolation(Throwable throwable) {
    return FOREIGN_KEY_VIOLATION_SQL_STATE.equals(extract(throwable).sqlState());
  }

  /**
   * Returns true if {@code throwable} represents one of the expected unique constraint names and is
   * not a non-uniqueness SQLSTATE (such as CHECK 23514, NOT NULL 23502, or FK 23503).
   */
  public static boolean matchesUniqueConstraint(Throwable throwable, String... expectedNames) {
    if (expectedNames == null || expectedNames.length == 0) {
      return false;
    }
    ViolationInfo info = extract(throwable);
    if (info.sqlState() != null && !UNIQUE_VIOLATION_SQL_STATE.equals(info.sqlState())) {
      return false;
    }
    if (info.constraintName() == null) {
      return false;
    }
    for (String expected : expectedNames) {
      if (expected != null && info.constraintName().equals(normalizeConstraintName(expected))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Maps a {@link DataIntegrityViolationException} globally when it is a unique violation. Returns
   * {@link Optional#empty()} for NOT NULL, CHECK, FK, data truncation, and unclassified errors so
   * callers keep HTTP 400.
   */
  public static Optional<AppErrorCode> classifyGlobal(DataIntegrityViolationException exception) {
    ViolationInfo info = extract(exception);
    if (info.sqlState() != null && !UNIQUE_VIOLATION_SQL_STATE.equals(info.sqlState())) {
      return Optional.empty();
    }

    if (info.constraintName() != null) {
      AppErrorCode mapped = GLOBAL_UNIQUE_CONSTRAINT_MAP.get(info.constraintName());
      if (mapped != null) {
        return Optional.of(mapped);
      }
    }

    if (UNIQUE_VIOLATION_SQL_STATE.equals(info.sqlState())) {
      return Optional.of(WebErrorCode.CONFLICT);
    }

    return Optional.empty();
  }

  private static ViolationInfo extractFromSqlExceptionChain(
      SQLException rootSqlEx, Set<Throwable> visited) {
    String constraintName = null;
    String sqlState = null;

    SQLException sqlEx = rootSqlEx;
    int guard = 0;
    while (sqlEx != null && guard++ < 15) {
      if (sqlState == null && hasText(sqlEx.getSQLState())) {
        sqlState = sqlEx.getSQLState().trim();
      }
      if (constraintName == null) {
        String serverConstraint = extractPostgresServerConstraint(sqlEx);
        if (hasText(serverConstraint)) {
          constraintName = normalizeConstraintName(serverConstraint);
        }
      }
      sqlEx = sqlEx.getNextException();
      if (sqlEx != null && !visited.add(sqlEx)) {
        break;
      }
    }

    return new ViolationInfo(constraintName, sqlState);
  }

  private static String extractPostgresServerConstraint(SQLException sqlEx) {
    try {
      Method getServerErrorMessage = sqlEx.getClass().getMethod("getServerErrorMessage");
      Object serverErrorMessage = getServerErrorMessage.invoke(sqlEx);
      if (serverErrorMessage == null) {
        return null;
      }
      Method getConstraint = serverErrorMessage.getClass().getMethod("getConstraint");
      Object constraint = getConstraint.invoke(serverErrorMessage);
      return constraint instanceof String s ? s : null;
    } catch (ReflectiveOperationException | SecurityException ignored) {
      return null;
    }
  }

  private static String extractConstraintFromMessages(Throwable root, String sqlState) {
    if (sqlState != null && !UNIQUE_VIOLATION_SQL_STATE.equals(sqlState)) {
      return null;
    }
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = root;
    while (current != null && visited.add(current) && visited.size() <= 25) {
      if ((UNIQUE_VIOLATION_SQL_STATE.equals(sqlState)
              || current instanceof ConstraintViolationException)
          && current.getMessage() != null) {
        Matcher matcher = PG_CONSTRAINT_PATTERN.matcher(current.getMessage());
        if (matcher.find()) {
          return normalizeConstraintName(matcher.group(1));
        }
      }
      current = current.getCause();
    }
    return null;
  }

  static String normalizeConstraintName(String rawName) {
    if (!hasText(rawName)) {
      return null;
    }
    String cleaned = rawName.trim().replace("\"", "").replace("`", "");
    int dotIndex = cleaned.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex < cleaned.length() - 1) {
      cleaned = cleaned.substring(dotIndex + 1);
    }
    return cleaned.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
