package com.erp.reimbursement.controller;

import com.erp.reimbursement.dto.MyClaimResponse;
import com.erp.reimbursement.dto.ReimbursementDtos.*;
import com.erp.reimbursement.entity.Reimbursement;
import com.erp.reimbursement.entity.ReimbursementAttachment;
import com.erp.reimbursement.service.ReimbursementService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reimbursements")
public class ReimbursementController {

    private final ReimbursementService s;

    public ReimbursementController(ReimbursementService s) {
        this.s = s;
    }

    // =========================================================
    // CREATE CLAIM
    // =========================================================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Reimbursement> create(
            @RequestPart("claim") @Valid CreateClaimRequest r,
            @RequestPart(value = "bill", required = false) MultipartFile bill,
            @RequestPart(value = "screenshotOfBill", required = false) MultipartFile screenshotOfBill,
            @RequestPart(value = "bills", required = false) List<MultipartFile> bills,
            @RequestPart(value = "evidence", required = false) List<MultipartFile> evidence) {

        MultipartFile finalBill = bill != null ? bill : (bills != null && !bills.isEmpty() ? bills.get(0) : null);
        MultipartFile finalScreenshot = screenshotOfBill != null ? screenshotOfBill : (evidence != null && !evidence.isEmpty() ? evidence.get(0) : null);

        return ResponseEntity
                .status(201)
                .body(s.create(r, finalBill, finalScreenshot));
    }

    // =========================================================
    // EMPLOYEE CLAIMS
    // =========================================================

    @GetMapping("/mine")
    public List<MyClaimResponse> mine() {
        return s.myClaims();
    }

    // =========================================================
    // MANAGER CLAIMS
    // =========================================================

    @GetMapping("/manager/claims")
    public List<com.erp.reimbursement.dto.ManagerClaimResponse> managerClaims() {
        return s.managerClaims();
    }

    @GetMapping("/manager/pending")
    public List<Reimbursement> managerPending() {
        return s.managerQueue();
    }

    // =========================================================
    // FINANCE CLAIMS
    // =========================================================

    @GetMapping("/finance/pending")
    public List<com.erp.reimbursement.dto.ManagerClaimResponse> financePending() {
        return s.financeQueue();
    }

    /**
     * Returns finance dashboard claims in a safe DTO format.
     *
     * URL:
     * GET /api/reimbursements/finance/claims
     */
    @GetMapping("/finance/claims")
    public List<com.erp.reimbursement.dto.ManagerClaimResponse> financeClaims() {
        return s.financeClaims();
    }

    // =========================================================
    // ALL CLAIMS
    // =========================================================

    @GetMapping("/all")
    public List<Reimbursement> all() {
        return s.all();
    }

    // =========================================================
    // SINGLE CLAIM
    // =========================================================

    @GetMapping("/{id}")
    public Reimbursement get(@PathVariable Long id) {
        return s.get(id);
    }

    // =========================================================
    // MANAGER APPROVE
    // =========================================================

    @PutMapping("/{id}/manager/approve")
    public ResponseEntity<Void> managerApprove(@PathVariable Long id) {

        s.managerAction(id, true, null);

        return ResponseEntity.ok().build();
    }

    // =========================================================
    // MANAGER REJECT
    // =========================================================

    @PutMapping("/{id}/manager/reject")
    public ResponseEntity<Void> managerReject(
            @PathVariable Long id,
            @RequestBody @Valid ActionRequest r) {

        s.managerAction(id, false, r.remark());

        return ResponseEntity.ok().build();
    }

    // =========================================================
    // FINANCE VERIFY / APPROVE
    // =========================================================

    @PutMapping("/{id}/finance/approve")
    public ResponseEntity<Void> financeApprove(@PathVariable Long id) {

        s.financeVerify(id, true, null);

        return ResponseEntity.ok().build();
    }

    // =========================================================
    // FINANCE REJECT
    // =========================================================

    @PutMapping("/{id}/finance/reject")
    public ResponseEntity<Void> financeReject(
            @PathVariable Long id,
            @RequestBody @Valid ActionRequest r) {

        s.financeVerify(id, false, r.remark());

        return ResponseEntity.ok().build();
    }

    // =========================================================
    // PAYMENT
    // =========================================================

    @PutMapping("/{id}/pay")
    public Reimbursement pay(
            @PathVariable Long id,
            @RequestBody @Valid PaymentRequest r) {

        return s.pay(id, r);
    }

    // =========================================================
    // ATTACHMENT DOWNLOAD / VIEW
    // =========================================================

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<Resource> attachment(
            @PathVariable Long attachmentId) {

        ReimbursementAttachment a = s.attachment(attachmentId);

        Resource res = new FileSystemResource(s.file(a));

        String contentType =
                a.getContentType() == null
                        ? "application/octet-stream"
                        : a.getContentType();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + a.getOriginalFileName() + "\""
                )
                .body(res);
    }
}