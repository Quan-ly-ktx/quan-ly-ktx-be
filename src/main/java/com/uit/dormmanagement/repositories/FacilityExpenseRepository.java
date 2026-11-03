package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.FacilityExpense;
import com.uit.dormmanagement.enums.ExpenseType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacilityExpenseRepository extends JpaRepository<FacilityExpense, UUID> {
  List<FacilityExpense> findByExpenseType(ExpenseType expenseType);

  List<FacilityExpense> findByExpenseDateBetween(LocalDate startDate, LocalDate endDate);
}
