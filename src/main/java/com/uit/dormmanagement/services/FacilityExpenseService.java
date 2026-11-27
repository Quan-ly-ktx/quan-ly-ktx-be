package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.expense.CreateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.expense.UpdateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptContent;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.enums.ExpenseType;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface FacilityExpenseService {

  PageResponse<FacilityExpenseResponse> getExpenses(
      String period, ExpenseType expenseType, String search, Pageable pageable);

  FacilityExpenseResponse getExpenseById(UUID id);

  FacilityExpenseResponse createExpense(UUID managerUserId, CreateFacilityExpenseRequest request);

  FacilityExpenseResponse updateExpense(UUID id, UpdateFacilityExpenseRequest request);

  void deleteExpense(UUID id);

  FacilityExpenseResponse uploadReceipt(UUID id, MultipartFile file);

  FacilityExpenseResponse deleteReceipt(UUID id);

  FacilityExpenseReceiptContent loadReceiptContent(UUID id);
}
