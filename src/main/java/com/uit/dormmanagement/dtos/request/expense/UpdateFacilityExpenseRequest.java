package com.uit.dormmanagement.dtos.request.expense;

import com.uit.dormmanagement.enums.ExpenseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFacilityExpenseRequest(
    @NotNull(message = "Loại chi phí không được để trống") ExpenseType expenseType,
    @NotBlank(message = "Tiêu đề chi phí không được để trống")
        @Size(max = 255, message = "Tiêu đề chi phí không được vượt quá 255 ký tự")
        String title,
    @NotNull(message = "Số tiền chi phí không được để trống") BigDecimal amount,
    @NotNull(message = "Ngày chi phí không được để trống") LocalDate expenseDate,
    @Size(max = 100, message = "Mã hóa đơn không được vượt quá 100 ký tự") String invoiceCode,
    @Size(max = 2000, message = "Ghi chú không được vượt quá 2000 ký tự") String notes) {}
