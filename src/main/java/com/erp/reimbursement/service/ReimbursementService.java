package com.erp.reimbursement.service;

import com.erp.reimbursement.dto.ManagerClaimResponse;
import com.erp.reimbursement.dto.MyClaimResponse;
import com.erp.reimbursement.dto.ReimbursementDtos.*;
import com.erp.reimbursement.entity.*;
import com.erp.reimbursement.enums.ClaimStatus;
import com.erp.reimbursement.enums.Role;
import com.erp.reimbursement.exception.ApiException;
import com.erp.reimbursement.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReimbursementService {

    private final ReimbursementRepository repo;
    private final ReimbursementAttachmentRepository attachmentRepo;
    private final ApprovalHistoryRepository approvalHistoryRepo;
    private final UserRepository userRepo;
    private final ExpenseCategoryRepository categoryRepo;
    private final ClaimTypeRepository claimTypeRepo;
    private final CurrentUserService currentUserService;

    private final Path uploadRoot;
    private final BillDetectionService billDetectionService;

    public ReimbursementService(
            ReimbursementRepository repo,
            ReimbursementAttachmentRepository attachmentRepo,
            ApprovalHistoryRepository approvalHistoryRepo,
            UserRepository userRepo,
            ExpenseCategoryRepository categoryRepo,
            ClaimTypeRepository claimTypeRepo,
            CurrentUserService currentUserService,
            BillDetectionService billDetectionService,
            @Value("${app.upload-dir:./uploads}") String uploadDir
    ) {
        this.repo = repo;
        this.attachmentRepo = attachmentRepo;
        this.approvalHistoryRepo = approvalHistoryRepo;
        this.userRepo = userRepo;
        this.categoryRepo = categoryRepo;
        this.claimTypeRepo = claimTypeRepo;
        this.currentUserService = currentUserService;
        this.billDetectionService = billDetectionService;
        this.uploadRoot = Paths.get(uploadDir);
    }

    // =========================================================
    // CREATE CLAIM
    // =========================================================

    @Transactional
    public Reimbursement create(
            CreateClaimRequest request,
            MultipartFile bill,
            MultipartFile screenshotOfBill
    ) {
        User employee = currentUserService.get();
        if (employee == null) {
            throw new ApiException("User not authenticated");
        }

        if (bill == null || bill.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bill document is required");
        }

        if (screenshotOfBill == null || screenshotOfBill.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment Evidence / Proof of Payment is required");
        }

        // Automatic Manager Routing via existing users.reporting_manager_id relationship
        User reportingManager = employee.getReportingManager();
        if (reportingManager == null || !reportingManager.isActive() || reportingManager.getRole() != Role.MANAGER) {
            throw new ApiException("Your reporting manager has not been assigned. Please contact the administrator.");
        }

        Reimbursement claim = new Reimbursement();
        claim.setEmployee(employee);
        claim.setTitle(request.title().trim());
        claim.setDescription(request.description() != null ? request.description().trim() : null);
        claim.setAmount(request.amount());
        claim.setExpenseDate(request.expenseDate());
        claim.setPaymentMode(request.paymentMode() != null ? request.paymentMode() : "Other");
        claim.setMerchant(request.merchant() != null ? request.merchant().trim() : null);
        claim.setStatus(ClaimStatus.PENDING_MANAGER_APPROVAL);
        claim.setSubmittedAt(LocalDateTime.now());

        if (request.claimTypeId() != null) {
            claimTypeRepo.findById(request.claimTypeId()).ifPresent(claim::setClaimType);
        }

        if (request.categoryIds() != null && !request.categoryIds().isEmpty()) {
            Set<ExpenseCategory> categories = new LinkedHashSet<>();
            for (Long catId : request.categoryIds()) {
                categoryRepo.findById(catId).ifPresent(categories::add);
            }
            claim.setCategories(categories);
        }

        Reimbursement saved = repo.save(claim);

        // Verify that the uploaded bill actually contains bill/invoice/receipt content before saving it.
        billDetectionService.validateBill(bill);

        // Save exactly the two supporting documents: 1. Bill, 2. Payment Evidence
        saveSingleAttachment(saved, bill, com.erp.reimbursement.enums.AttachmentType.BILL_RECEIPT);
        saveSingleAttachment(saved, screenshotOfBill, com.erp.reimbursement.enums.AttachmentType.PAYMENT_EVIDENCE);

        return repo.save(saved);
    }

    @Transactional
    public Reimbursement create(
            CreateClaimRequest request,
            List<MultipartFile> bills,
            List<MultipartFile> evidence
    ) {
        MultipartFile bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
        MultipartFile screenshot = (evidence != null && !evidence.isEmpty()) ? evidence.get(0) : null;
        return create(request, bill, screenshot);
    }

    // =========================================================
    // EMPLOYEE - MY CLAIMS
    // =========================================================

    @Transactional(readOnly = true)
    public List<MyClaimResponse> myClaims() {
        User employee = currentUserService.get();
        if (employee == null) {
            throw new ApiException("User not authenticated");
        }

        return repo.findByEmployeeIdOrderBySubmittedAtDesc(employee.getId())
                .stream()
                .filter(Objects::nonNull)
                .map(this::toMyClaimResponse)
                .toList();
    }

    // =========================================================
    // FINANCE - CLAIMS
    // =========================================================

    @Transactional(readOnly = true)
    public List<ManagerClaimResponse> financeClaims() {
        User financeUser = currentUserService.get();
        if (financeUser.getRole() != Role.FINANCE && financeUser.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Finance or Admin role required");
        }

        List<Reimbursement> allClaims = repo.findAll();
        if (allClaims == null || allClaims.isEmpty()) {
            return new ArrayList<>();
        }

        return allClaims.stream()
                .filter(Objects::nonNull)
                .filter(claim -> {
                    ClaimStatus status = claim.getStatus();
                    return status == ClaimStatus.PENDING_FINANCE_VERIFICATION
                            || status == ClaimStatus.PENDING_PAYMENT
                            || status == ClaimStatus.PAID
                            || status == ClaimStatus.REJECTED;
                })
                .sorted(Comparator.comparing(Reimbursement::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toManagerClaimResponse)
                .toList();
    }

    // =========================================================
    // MANAGER CLAIMS
    // =========================================================

    @Transactional(readOnly = true)
    public List<ManagerClaimResponse> managerClaims() {
        User manager = currentUserService.get();
        if (manager.getRole() != Role.MANAGER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Manager role required");
        }

        return repo.findByEmployeeReportingManagerIdOrderBySubmittedAtDesc(manager.getId())
                .stream()
                .filter(Objects::nonNull)
                .map(this::toManagerClaimResponse)
                .toList();
    }

    // =========================================================
    // MANAGER QUEUE
    // =========================================================

    @Transactional(readOnly = true)
    public List<Reimbursement> managerQueue() {
        User manager = currentUserService.get();
        if (manager.getRole() != Role.MANAGER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Manager role required");
        }

        return repo.findByEmployeeReportingManagerIdAndStatusOrderBySubmittedAtAsc(
                manager.getId(),
                ClaimStatus.PENDING_MANAGER_APPROVAL
        );
    }

    // =========================================================
    // FINANCE QUEUE
    // =========================================================

    @Transactional(readOnly = true)
    public List<ManagerClaimResponse> financeQueue() {
        User finance = currentUserService.get();
        if (finance == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        if (finance.getRole() != Role.FINANCE && finance.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Finance or Admin role required");
        }

        return repo.findAll().stream()
                .filter(Objects::nonNull)
                .filter(claim -> claim.getStatus() == ClaimStatus.PENDING_FINANCE_VERIFICATION)
                .sorted(Comparator.comparing(
                        Reimbursement::getSubmittedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(this::toManagerClaimResponse)
                .toList();
    }

    // =========================================================
    // ALL CLAIMS (Admin / Finance only)
    // =========================================================

    @Transactional(readOnly = true)
    public List<Reimbursement> all() {
        User user = currentUserService.get();
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.FINANCE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied: Admin or Finance role required");
        }
        return repo.findAll();
    }

    // =========================================================
    // GET SINGLE CLAIM WITH STRICT IDOR AUTHORIZATION
    // =========================================================

    @Transactional(readOnly = true)
    public Reimbursement get(Long id) {
        Reimbursement claim = repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claim not found: " + id));

        User currentUser = currentUserService.get();
        checkClaimAccess(claim, currentUser);

        return claim;
    }

    // =========================================================
    // MANAGER ACTION (APPROVE / REJECT)
    // =========================================================

    @Transactional
    public void managerAction(Long id, boolean approve, String remark) {
        User manager = currentUserService.get();
        if (manager.getRole() != Role.MANAGER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Manager role required");
        }

        Reimbursement claim = repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claim not found: " + id));

        if (claim.getEmployee() == null) {
            throw new ApiException("Claim employee information missing");
        }

        User reportingManager = claim.getEmployee().getReportingManager();
        if (reportingManager == null || !Objects.equals(reportingManager.getId(), manager.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied: You are not the reporting manager for this claim");
        }

        if (claim.getStatus() != ClaimStatus.PENDING_MANAGER_APPROVAL) {
            throw new ApiException("Claim is not pending manager approval");
        }

        if (!approve && (remark == null || remark.isBlank())) {
            throw new ApiException("Rejection reason is required");
        }

        claim.setManagerActionAt(LocalDateTime.now());

        if (approve) {
            claim.setStatus(ClaimStatus.PENDING_FINANCE_VERIFICATION);
            addApprovalHistory(claim, manager, "MANAGER_APPROVED", remark);
        } else {
            claim.setStatus(ClaimStatus.REJECTED);
            claim.setRejectionRemark(remark);
            addApprovalHistory(claim, manager, "MANAGER_REJECTED", remark);
        }

        repo.save(claim);
    }

    // =========================================================
    // FINANCE VERIFY
    // =========================================================

    @Transactional
    public void financeVerify(Long id, boolean approve, String remark) {
        User finance = currentUserService.get();
        if (finance.getRole() != Role.FINANCE && finance.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Finance role required");
        }

        Reimbursement claim = repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claim not found: " + id));

        if (claim.getStatus() != ClaimStatus.PENDING_FINANCE_VERIFICATION) {
            throw new ApiException("Claim is not pending finance verification");
        }

        if (!approve && (remark == null || remark.isBlank())) {
            throw new ApiException("Rejection reason is required");
        }

        claim.setFinanceActionAt(LocalDateTime.now());

        if (approve) {
            claim.setStatus(ClaimStatus.PENDING_PAYMENT);
            addApprovalHistory(claim, finance, "FINANCE_APPROVED", remark);
        } else {
            claim.setStatus(ClaimStatus.REJECTED);
            claim.setRejectionRemark(remark);
            addApprovalHistory(claim, finance, "FINANCE_REJECTED", remark);
        }

        repo.save(claim);
    }

    // =========================================================
    // PAYMENT
    // =========================================================

    @Transactional
    public Reimbursement pay(Long id, PaymentRequest request) {
        User finance = currentUserService.get();
        if (finance.getRole() != Role.FINANCE && finance.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Finance role required");
        }

        Reimbursement claim = repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claim not found: " + id));

        if (claim.getStatus() != ClaimStatus.PENDING_PAYMENT) {
            throw new ApiException("Claim is not pending payment");
        }

        claim.setStatus(ClaimStatus.PAID);
        claim.setPaidAt(LocalDateTime.now());

        return repo.save(claim);
    }

    // =========================================================
    // ATTACHMENT ACCESS WITH STRICT AUTHORIZATION
    // =========================================================

    @Transactional(readOnly = true)
    public ReimbursementAttachment attachment(Long attachmentId) {
        ReimbursementAttachment attachment = attachmentRepo.findById(attachmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attachment not found: " + attachmentId));

        User currentUser = currentUserService.get();
        checkClaimAccess(attachment.getReimbursement(), currentUser);

        return attachment;
    }

    public Path file(ReimbursementAttachment attachment) {
        if (attachment == null) {
            throw new ApiException("Attachment is missing");
        }

        String storedFileName = attachment.getStoredFileName();
        if (storedFileName == null || storedFileName.isBlank()) {
            throw new ApiException("Stored file name is missing");
        }

        return uploadRoot.resolve(storedFileName).normalize();
    }

    // =========================================================
    // AUTHORIZATION CHECK HELPER
    // =========================================================

    private void checkClaimAccess(Reimbursement claim, User currentUser) {
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.FINANCE) {
            return; // Admins and Finance can view
        }

        if (currentUser.getRole() == Role.EMPLOYEE) {
            if (claim.getEmployee() == null || !Objects.equals(claim.getEmployee().getId(), currentUser.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Access denied: You are not authorized to view this claim");
            }
            return;
        }

        if (currentUser.getRole() == Role.MANAGER) {
            if (claim.getEmployee() == null
                    || claim.getEmployee().getReportingManager() == null
                    || !Objects.equals(claim.getEmployee().getReportingManager().getId(), currentUser.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Access denied: You are not the reporting manager for this claim");
            }
            return;
        }

        throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }

    // =========================================================
    // SAVE ATTACHMENTS
    // =========================================================

    private void saveSingleAttachment(
            Reimbursement claim,
            MultipartFile file,
            com.erp.reimbursement.enums.AttachmentType type
    ) {
        if (file == null || file.isEmpty()) {
            return;
        }

        try {
            Files.createDirectories(uploadRoot);

            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                originalName = type == com.erp.reimbursement.enums.AttachmentType.BILL_RECEIPT ? "bill" : "screenshot";
            }

            String safeOriginalName = Paths.get(originalName).getFileName().toString();
            String storedName = System.currentTimeMillis() + "_" + safeOriginalName;

            Path target = uploadRoot.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            ReimbursementAttachment attachment = new ReimbursementAttachment();
            attachment.setReimbursement(claim);
            attachment.setAttachmentType(type);
            attachment.setOriginalFileName(safeOriginalName);
            attachment.setStoredFileName(storedName);
            attachment.setContentType(file.getContentType());
            attachment.setFileSize(file.getSize() >= 0 ? file.getSize() : 0L);

            attachmentRepo.save(attachment);
        } catch (IOException ex) {
            throw new ApiException("Unable to save attachment: " + ex.getMessage());
        }
    }

    private void saveAttachments(
            Reimbursement claim,
            List<MultipartFile> files,
            com.erp.reimbursement.enums.AttachmentType type
    ) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (MultipartFile f : files) {
            saveSingleAttachment(claim, f, type);
        }
    }

    // =========================================================
    // APPROVAL HISTORY
    // =========================================================

    private void addApprovalHistory(
            Reimbursement claim,
            User performedBy,
            String action,
            String remark
    ) {
        ApprovalHistory history = new ApprovalHistory();
        history.setReimbursement(claim);
        history.setPerformedBy(performedBy);
        history.setAction(action);
        history.setRemark(remark);
        history.setCreatedAt(LocalDateTime.now());

        approvalHistoryRepo.save(history);
    }

    // =========================================================
    // DTO MAPPERS
    // =========================================================

    private MyClaimResponse toMyClaimResponse(Reimbursement claim) {
        List<MyClaimResponse.CategoryResponse> categories = new ArrayList<>();
        if (claim.getCategories() != null) {
            categories = claim.getCategories().stream()
                    .filter(Objects::nonNull)
                    .map(c -> new MyClaimResponse.CategoryResponse(c.getId(), c.getName()))
                    .toList();
        }

        List<MyClaimResponse.AttachmentResponse> attachments = new ArrayList<>();
        if (claim.getAttachments() != null) {
            attachments = claim.getAttachments().stream()
                    .filter(Objects::nonNull)
                    .map(a -> new MyClaimResponse.AttachmentResponse(
                            a.getId(),
                            a.getAttachmentType() != null ? a.getAttachmentType().name() : "OTHER",
                            a.getOriginalFileName(),
                            a.getContentType(),
                            a.getFileSize()
                    ))
                    .toList();
        }

        List<MyClaimResponse.ApprovalHistoryResponse> history = new ArrayList<>();
        if (claim.getApprovalHistory() != null) {
            history = claim.getApprovalHistory().stream()
                    .filter(Objects::nonNull)
                    .map(h -> new MyClaimResponse.ApprovalHistoryResponse(
                            h.getId(),
                            h.getAction(),
                            h.getPerformedBy() != null ? h.getPerformedBy().getFullName() : null,
                            h.getPerformedBy() != null && h.getPerformedBy().getRole() != null ? h.getPerformedBy().getRole().name() : null,
                            h.getRemark(),
                            h.getCreatedAt()
                    )).toList();
        }

        return new MyClaimResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getTitle(),
                categories,
                claim.getExpenseDate(),
                claim.getAmount(),
                claim.getPaymentMode(),
                claim.getDescription(),
                claim.getMerchant(),
                claim.getStatus() != null ? claim.getStatus().name() : null,
                claim.getRejectionRemark(),
                claim.getSubmittedAt(),
                claim.getManagerActionAt(),
                claim.getFinanceActionAt(),
                claim.getPaidAt(),
                attachments,
                history
        );
    }

    private ManagerClaimResponse toManagerClaimResponse(Reimbursement claim) {
        ManagerClaimResponse.ReimbursementUserResponse empDto = null;
        if (claim.getEmployee() != null) {
            User emp = claim.getEmployee();
            empDto = new ManagerClaimResponse.ReimbursementUserResponse(
                    emp.getId(),
                    emp.getEmployeeId(),
                    emp.getFirstName(),
                    emp.getLastName(),
                    emp.getEmail(),
                    emp.getDepartment() != null ? emp.getDepartment().getName() : null
            );
        }

        List<ManagerClaimResponse.CategoryResponse> categories = new ArrayList<>();
        if (claim.getCategories() != null) {
            categories = claim.getCategories().stream()
                    .filter(Objects::nonNull)
                    .map(c -> new ManagerClaimResponse.CategoryResponse(c.getId(), c.getName()))
                    .toList();
        }

        List<ManagerClaimResponse.AttachmentResponse> attachments = new ArrayList<>();
        if (claim.getAttachments() != null) {
            attachments = claim.getAttachments().stream()
                    .filter(Objects::nonNull)
                    .map(a -> new ManagerClaimResponse.AttachmentResponse(
                            a.getId(),
                            a.getAttachmentType() != null ? a.getAttachmentType().name() : "OTHER",
                            a.getAttachmentType() != null ? a.getAttachmentType().name() : "OTHER",
                            a.getOriginalFileName(),
                            a.getStoredFileName(),
                            a.getContentType(),
                            a.getFileSize(),
                            a.getUploadedAt(),
                            claim.getId()
                    ))
                    .toList();
        }

        List<ManagerClaimResponse.ApprovalHistoryResponse> history = new ArrayList<>();
        if (claim.getApprovalHistory() != null) {
            history = claim.getApprovalHistory().stream()
                    .filter(Objects::nonNull)
                    .map(h -> new ManagerClaimResponse.ApprovalHistoryResponse(
                            h.getId(),
                            h.getAction(),
                            h.getPerformedBy() != null ? h.getPerformedBy().getFullName() : null,
                            h.getPerformedBy() != null && h.getPerformedBy().getRole() != null ? h.getPerformedBy().getRole().name() : null,
                            h.getAction(),
                            h.getRemark(),
                            h.getCreatedAt()
                    ))
                    .toList();
        }

        return new ManagerClaimResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getTitle(),
                claim.getDescription(),
                claim.getAmount(),
                claim.getExpenseDate(),
                claim.getPaymentMode(),
                claim.getMerchant(),
                claim.getStatus() != null ? claim.getStatus().name() : null,
                claim.getStatus() != null ? claim.getStatus().name() : null,
                claim.getRejectionRemark(),
                claim.getSubmittedAt(),
                empDto,
                categories,
                attachments,
                history
        );
    }
}