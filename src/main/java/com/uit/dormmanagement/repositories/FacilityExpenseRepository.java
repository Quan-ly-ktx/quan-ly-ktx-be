package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.FacilityExpense;
import com.uit.dormmanagement.enums.ExpenseType;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FacilityExpenseRepository extends JpaRepository<FacilityExpense, UUID> {

  interface ExpenseTypeSumProjection {
    ExpenseType getExpenseType();

    BigDecimal getTotalAmount();
  }

  List<FacilityExpense> findByExpenseType(ExpenseType expenseType);

  List<FacilityExpense> findByExpenseDateBetween(LocalDate startDate, LocalDate endDate);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT fe FROM FacilityExpense fe WHERE fe.id = :id")
  Optional<FacilityExpense> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      value =
          """
          SELECT fe FROM FacilityExpense fe
          JOIN FETCH fe.recordedByStaff s
          WHERE (CAST(:startDate AS LocalDate) IS NULL OR fe.expenseDate >= :startDate)
            AND (CAST(:endDate AS LocalDate) IS NULL OR fe.expenseDate <= :endDate)
            AND (:expenseType IS NULL OR fe.expenseType = :expenseType)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(fe.title) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(fe.invoiceCode, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(fe.notes, '')) LIKE LOWER(CONCAT('%', :search, '%')))
          """,
      countQuery =
          """
          SELECT COUNT(fe) FROM FacilityExpense fe
          WHERE (CAST(:startDate AS LocalDate) IS NULL OR fe.expenseDate >= :startDate)
            AND (CAST(:endDate AS LocalDate) IS NULL OR fe.expenseDate <= :endDate)
            AND (:expenseType IS NULL OR fe.expenseType = :expenseType)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(fe.title) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(fe.invoiceCode, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(fe.notes, '')) LIKE LOWER(CONCAT('%', :search, '%')))
          """)
  Page<FacilityExpense> findExpensesWithFilters(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("expenseType") ExpenseType expenseType,
      @Param("search") String search,
      Pageable pageable);

  List<FacilityExpense> findByExpenseDateBetweenOrderByExpenseDateDescCreatedAtDesc(
      LocalDate startDate, LocalDate endDate);

  boolean existsByExpenseTypeAndExpenseDateBetween(
      ExpenseType expenseType, LocalDate startDate, LocalDate endDate);

  boolean existsByExpenseTypeAndExpenseDateBetweenAndIdNot(
      ExpenseType expenseType, LocalDate startDate, LocalDate endDate, UUID id);

  @Query(
      """
      SELECT fe.expenseType AS expenseType, SUM(fe.amount) AS totalAmount
      FROM FacilityExpense fe
      WHERE fe.expenseDate >= :startDate AND fe.expenseDate <= :endDate
      GROUP BY fe.expenseType
      """)
  List<ExpenseTypeSumProjection> sumAmountByExpenseTypeBetween(
      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
