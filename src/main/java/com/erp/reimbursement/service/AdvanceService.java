package com.erp.reimbursement.service;

import com.erp.reimbursement.dto.AdvanceDtos.*;
import com.erp.reimbursement.entity.*;
import com.erp.reimbursement.enums.*;
import com.erp.reimbursement.exception.ApiException;
import com.erp.reimbursement.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class AdvanceService {

    private final AdvanceRequestRepository repo;
    private final CurrentUserService current;

    public AdvanceService(AdvanceRequestRepository r, CurrentUserService c) {
        repo = r;
        current = c;
    }

    @Transactional(readOnly = true)
    public List<AdvanceResponse> mine() {
        return repo.findByEmployeeIdOrderByCreatedAtDesc(current.get().getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvanceResponse> managerAll() {
        User m = current.get();
        if (m.getRole() != Role.MANAGER) throw new ApiException("Manager role required");
        return repo.findByEmployeeReportingManagerIdOrderByCreatedAtDesc(m.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvanceResponse> managerPending() {
        User m = current.get();
        if (m.getRole() != Role.MANAGER) throw new ApiException("Manager role required");
        return repo.findByEmployeeReportingManagerIdAndStatusOrderByCreatedAtAsc(m.getId(), AdvanceStatus.PENDING_MANAGER_APPROVAL)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvanceResponse> financePending() {
        User f = current.get();
        if (f.getRole() != Role.FINANCE && f.getRole() != Role.ADMIN) {
            throw new ApiException("Finance or Admin role required");
        }
        return repo.findByStatusOrderByCreatedAtAsc(AdvanceStatus.PENDING_FINANCE_PAYMENT)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvanceResponse> all() {
        User u = current.get();
        if (u.getRole() != Role.FINANCE && u.getRole() != Role.ADMIN) {
            throw new ApiException("Finance or Admin role required");
        }
        return repo.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public AdvanceResponse create(CreateAdvanceRequest r) {
        User e = current.get();
        if (e.getRole() != Role.EMPLOYEE) throw new ApiException("Only employees can submit advance requests");
        AdvanceRequest a = new AdvanceRequest();
        a.setEmployee(e);
        a.setCategory(r.effectiveCategory());
        a.setPurpose(r.effectivePurpose());
        a.setAmount(r.amount());
        a.setRequiredDate(r.effectiveDate());
        a.setStatus(AdvanceStatus.PENDING_MANAGER_APPROVAL);
        return mapToResponse(repo.save(a));
    }

    @Transactional
    public AdvanceResponse managerAction(Long id, boolean approve, String remark) {
        User m = current.get();
        if (m.getRole() != Role.MANAGER) throw new ApiException("Manager role required");
        AdvanceRequest a = getEntity(id);
        if (a.getEmployee().getReportingManager() == null || !Objects.equals(a.getEmployee().getReportingManager().getId(), m.getId())) {
            throw new ApiException("This request is not assigned to you");
        }
        if (a.getStatus() != AdvanceStatus.PENDING_MANAGER_APPROVAL) {
            throw new ApiException("Advance is not pending manager approval");
        }
        if (!approve && (remark == null || remark.isBlank())) {
            throw new ApiException("Rejection remark is required");
        }
        a.setStatus(approve ? AdvanceStatus.PENDING_FINANCE_PAYMENT : AdvanceStatus.REJECTED);
        a.setRejectionRemark(approve ? null : remark);
        return mapToResponse(repo.save(a));
    }

    @Transactional
    public AdvanceResponse pay(Long id, AdvancePaymentRequest r) {
        User f = current.get();
        if (f.getRole() != Role.FINANCE) throw new ApiException("Finance role required");
        AdvanceRequest a = getEntity(id);
        if (a.getStatus() != AdvanceStatus.PENDING_FINANCE_PAYMENT) {
            throw new ApiException("Advance is not pending finance payment");
        }
        a.setStatus(AdvanceStatus.PAID);
        a.setPaymentType(r.paymentType());
        a.setTransactionReference(r.transactionReference() == null || r.transactionReference().isBlank() ? "ADV-TXN-" + System.currentTimeMillis() : r.transactionReference());
        a.setPaidAt(LocalDateTime.now());
        return mapToResponse(repo.save(a));
    }

    @Transactional(readOnly = true)
    public AdvanceResponse get(Long id) {
        AdvanceRequest a = getEntity(id);
        User u = current.get();
        boolean allowed = u.getRole() == Role.ADMIN || u.getRole() == Role.FINANCE;
        if (u.getRole() == Role.EMPLOYEE) {
            allowed = a.getEmployee() != null && Objects.equals(a.getEmployee().getId(), u.getId());
        } else if (u.getRole() == Role.MANAGER) {
            allowed = a.getEmployee() != null
                    && a.getEmployee().getReportingManager() != null
                    && Objects.equals(a.getEmployee().getReportingManager().getId(), u.getId());
        }
        if (!allowed) throw new ApiException("Access denied for this advance request");
        return mapToResponse(a);
    }

    private AdvanceRequest getEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException("Advance request not found"));
    }

    private AdvanceResponse mapToResponse(AdvanceRequest a) {
        AdvanceUserDto empDto = null;
        if (a.getEmployee() != null) {
            User e = a.getEmployee();
            empDto = new AdvanceUserDto(
                e.getId(),
                e.getEmployeeId(),
                e.getFirstName(),
                e.getLastName(),
                e.getEmail(),
                e.getDepartment() != null ? e.getDepartment().getName() : null
            );
        }
        return new AdvanceResponse(
            a.getId(),
            a.getRequestNumber(),
            a.getAmount(),
            a.getCategory(),
            a.getPurpose(),
            a.getRequiredDate() != null ? a.getRequiredDate().toString() : null,
            a.getStatus() != null ? a.getStatus().name() : null,
            a.getRejectionRemark(),
            a.getPaymentType(),
            a.getTransactionReference(),
            a.getCreatedAt() != null ? a.getCreatedAt().toString() : null,
            a.getPaidAt() != null ? a.getPaidAt().toString() : null,
            empDto
        );
    }
}
