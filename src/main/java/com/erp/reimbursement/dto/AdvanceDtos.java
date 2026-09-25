package com.erp.reimbursement.dto;

import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;

public final class AdvanceDtos {
    private AdvanceDtos() {}

    public record CreateAdvanceRequest(
        String category,
        String purpose,
        String reason,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        LocalDate requiredDate,
        LocalDate expectedExpenseDate
    ) {
        public String effectivePurpose() {
            if (purpose != null && !purpose.isBlank()) return purpose.trim();
            if (reason != null && !reason.isBlank()) return reason.trim();
            return "Advance Request";
        }

        public LocalDate effectiveDate() {
            if (requiredDate != null) return requiredDate;
            if (expectedExpenseDate != null) return expectedExpenseDate;
            return LocalDate.now();
        }

        public String effectiveCategory() {
            if (category != null && !category.isBlank()) return category.trim();
            return "General";
        }
    }

    public record AdvancePaymentRequest(
        @NotBlank String paymentType,
        String transactionReference
    ) {}

    public record ActionRequest(
        String remark
    ) {}

    public record AdvanceUserDto(
        Long id,
        String employeeId,
        String firstName,
        String lastName,
        String email,
        String departmentName
    ) {}

    public record AdvanceResponse(
        Long id,
        String advanceNumber,
        BigDecimal amount,
        String category,
        String reason,
        String expectedExpenseDate,
        String status,
        String rejectionRemark,
        String paymentType,
        String transactionReference,
        String createdAt,
        String paidAt,
        AdvanceUserDto employee
    ) {}
}
