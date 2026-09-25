package com.erp.reimbursement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MyClaimResponse(
        Long id,
        String claimNumber,
        String title,
        List<CategoryResponse> categories,
        LocalDate expenseDate,
        BigDecimal amount,
        String paymentMode,
        String description,
        String merchant,
        String status,
        String rejectionRemark,
        LocalDateTime submittedAt,
        LocalDateTime managerActionAt,
        LocalDateTime financeActionAt,
        LocalDateTime paidAt,
        List<AttachmentResponse> attachments,
        List<ApprovalHistoryResponse> approvalHistory
) {

    public record CategoryResponse(
            Long id,
            String name
    ) {}

    public record AttachmentResponse(
            Long id,
            String attachmentType,
            String originalFileName,
            String contentType,
            long fileSize
    ) {}

    public record ApprovalHistoryResponse(
            Long id,
            String action,
            String actorName,
            String actorRole,
            String remark,
            LocalDateTime actionDate
    ) {}
}