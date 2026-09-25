package com.erp.reimbursement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ManagerClaimResponse(
        Long id,
        String claimNumber,
        String title,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        String paymentMode,
        String merchant,
        String status,
        String currentStage,
        String rejectionRemark,
        LocalDateTime createdAt,
        ReimbursementUserResponse employee,
        List<CategoryResponse> categories,
        List<AttachmentResponse> attachments,
        List<ApprovalHistoryResponse> approvalHistory
) {

    public record ReimbursementUserResponse(
            Long id,
            String employeeId,
            String firstName,
            String lastName,
            String email,
            String departmentName
    ) {}

    public record CategoryResponse(
            Long id,
            String name
    ) {}

    public record AttachmentResponse(
            Long id,
            String attachmentType,
            String type,
            String originalFileName,
            String storedFileName,
            String contentType,
            Long fileSize,
            LocalDateTime uploadedAt,
            Long reimbursementId
    ) {}

    public record ApprovalHistoryResponse(
            Long id,
            String levelName,
            String actorName,
            String actorRole,
            String status,
            String remark,
            LocalDateTime actionDate
    ) {}
}