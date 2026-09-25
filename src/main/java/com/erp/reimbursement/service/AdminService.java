package com.erp.reimbursement.service;

import com.erp.reimbursement.dto.AdminDtos.DepartmentRequest;
import com.erp.reimbursement.dto.AdminDtos.UserRequest;
import com.erp.reimbursement.entity.Department;
import com.erp.reimbursement.entity.User;
import com.erp.reimbursement.enums.Role;
import com.erp.reimbursement.exception.ApiException;
import com.erp.reimbursement.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class AdminService {

    private final UserRepository users;
    private final DepartmentRepository deps;
    private final ReimbursementRepository reimbursements;
    private final AdvanceRequestRepository advances;
    private final ApprovalHistoryRepository approvals;
    private final PaymentRepository payments;
    private final PasswordEncoder enc;
    private final CurrentUserService currentUserService;

    public AdminService(
            UserRepository users,
            DepartmentRepository deps,
            ReimbursementRepository reimbursements,
            AdvanceRequestRepository advances,
            ApprovalHistoryRepository approvals,
            PaymentRepository payments,
            PasswordEncoder enc,
            CurrentUserService currentUserService
    ) {
        this.users = users;
        this.deps = deps;
        this.reimbursements = reimbursements;
        this.advances = advances;
        this.approvals = approvals;
        this.payments = payments;
        this.enc = enc;
        this.currentUserService = currentUserService;
    }

    public List<User> users() {
        return users.findAll();
    }

    public List<User> managers() {
        return users.findAll().stream()
                .filter(u -> u.getRole() == Role.MANAGER && u.isActive())
                .toList();
    }

    @Transactional
    public User create(UserRequest r) {
        String empId = (r.employeeId() != null && !r.employeeId().isBlank())
                ? r.employeeId().trim()
                : "EMP-" + System.currentTimeMillis();

        if (users.findByEmployeeId(empId).isPresent()) {
            throw new ApiException("Employee ID already exists: " + empId);
        }
        if (users.findByEmailIgnoreCase(r.email().trim()).isPresent()) {
            throw new ApiException("Email already exists: " + r.email());
        }
        if (users.findByUsernameIgnoreCase(r.username().trim()).isPresent()) {
            throw new ApiException("Username already exists: " + r.username());
        }

        String actor = resolveAuthenticatedAdmin();

        User u = new User();
        u.setEmployeeId(empId);
        u.setCreatedBy(actor);
        u.setUpdatedBy(null);
        u.setUpdatedAt(null);

        apply(u, r, true);
        return users.save(u);
    }

    @Transactional
    public User update(Long id, UserRequest r) {
        User u = users.findById(id).orElseThrow(() -> new ApiException("User not found: " + id));

        // Uniqueness checks if updated
        if (r.email() != null) {
            Optional<User> existingEmail = users.findByEmailIgnoreCase(r.email().trim());
            if (existingEmail.isPresent() && !Objects.equals(existingEmail.get().getId(), id)) {
                throw new ApiException("Email already in use: " + r.email());
            }
        }

        if (r.username() != null) {
            Optional<User> existingUser = users.findByUsernameIgnoreCase(r.username().trim());
            if (existingUser.isPresent() && !Objects.equals(existingUser.get().getId(), id)) {
                throw new ApiException("Username already in use: " + r.username());
            }
        }

        String actor = resolveAuthenticatedAdmin();
        u.setUpdatedBy(actor);

        apply(u, r, false);
        return users.save(u);
    }

    private void apply(User u, UserRequest r, boolean create) {
        if (r.employeeId() != null && !r.employeeId().isBlank()) {
            u.setEmployeeId(r.employeeId().trim());
        }
        u.setFirstName(r.firstName().trim());
        u.setLastName(r.lastName().trim());
        u.setEmail(r.email().trim().toLowerCase());
        u.setUsername(r.username().trim());

        if (create && (r.password() == null || r.password().isBlank())) {
            throw new ApiException("Password is required for a new user");
        }
        if (r.password() != null && !r.password().isBlank()) {
            u.setPassword(enc.encode(r.password()));
        }

        u.setMobile(r.mobile());
        u.setAlternateMobile(r.alternateMobile());
        u.setDateOfBirth(r.dateOfBirth());
        u.setDesignation(r.designation());
        u.setRole(r.role());
        u.setActive(r.active() == null || r.active());

        u.setDepartment(r.departmentId() == null ? null : deps.findById(r.departmentId())
                .orElseThrow(() -> new ApiException("Department not found: " + r.departmentId())));

        // Validate and assign Reporting Manager
        if (r.reportingManagerId() != null) {
            if (!create && u.getId() != null && Objects.equals(u.getId(), r.reportingManagerId())) {
                throw new ApiException("An employee cannot be assigned as their own reporting manager");
            }

            User manager = users.findById(r.reportingManagerId())
                    .orElseThrow(() -> new ApiException("Reporting manager not found with ID: " + r.reportingManagerId()));

            if (!manager.isActive()) {
                throw new ApiException("Selected reporting manager is inactive");
            }
            if (manager.getRole() != Role.MANAGER) {
                throw new ApiException("Selected user must have the MANAGER role");
            }

            u.setReportingManager(manager);
        } else if (r.role() == Role.EMPLOYEE) {
            throw new ApiException("A reporting manager must be assigned for an employee");
        } else {
            u.setReportingManager(null);
        }
    }

    private String resolveAuthenticatedAdmin() {
        try {
            User admin = currentUserService.get();
            if (admin != null) {
                return (admin.getEmail() != null && !admin.getEmail().isBlank())
                        ? admin.getEmail()
                        : admin.getUsername();
            }
        } catch (Exception ignored) {
        }
        return "admin@example.com";
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        User u = users.findById(id).orElseThrow(() -> new ApiException("User not found with ID: " + id));

        boolean hasClaims = !reimbursements.findByEmployeeIdOrderBySubmittedAtDesc(id).isEmpty();
        boolean hasAdvances = !advances.findByEmployeeIdOrderByCreatedAtDesc(id).isEmpty();
        boolean hasReportingEmployees = !users.findByReportingManagerId(id).isEmpty();
        boolean hasApprovals = approvals.existsByPerformedById(id);
        boolean hasPayments = payments.existsByPaidById(id);

        if (hasClaims || hasAdvances || hasReportingEmployees || hasApprovals || hasPayments) {
            u.setActive(false);
            u.setUpdatedBy(resolveAuthenticatedAdmin());
            users.save(u);
            return Map.of(
                    "message", "User has linked historical records or reporting employees. Account has been safely deactivated to preserve audit trails.",
                    "deactivated", true,
                    "deleted", false
            );
        }

        users.deleteById(id);
        return Map.of(
                "message", "User deleted successfully.",
                "deactivated", false,
                "deleted", true
        );
    }

    public List<Department> departments() {
        return deps.findAllByOrderByNameAsc();
    }

    @Transactional
    public Department createDepartment(DepartmentRequest r) {
        String name = r.name().trim();
        if (deps.findByNameIgnoreCase(name).isPresent()) {
            throw new ApiException("Department already exists: " + name);
        }
        Department d = new Department();
        d.setName(name);
        d.setActive(r.active() == null || r.active());
        return deps.save(d);
    }

    @Transactional
    public Department updateDepartment(Long id, DepartmentRequest r) {
        Department d = deps.findById(id).orElseThrow(() -> new ApiException("Department not found with ID: " + id));
        String newName = r.name().trim();
        Optional<Department> existing = deps.findByNameIgnoreCase(newName);
        if (existing.isPresent() && !Objects.equals(existing.get().getId(), id)) {
            throw new ApiException("Department name already in use: " + newName);
        }
        d.setName(newName);
        if (r.active() != null) {
            d.setActive(r.active());
        }
        return deps.save(d);
    }

    @Transactional
    public Map<String, Object> deleteDepartment(Long id) {
        Department d = deps.findById(id).orElseThrow(() -> new ApiException("Department not found with ID: " + id));
        boolean hasUsers = users.findAll().stream()
                .anyMatch(u -> u.getDepartment() != null && Objects.equals(u.getDepartment().getId(), id));

        if (hasUsers) {
            d.setActive(false);
            deps.save(d);
            return Map.of(
                    "message", "Department has assigned users. It was safely deactivated to preserve organization history.",
                    "deactivated", true,
                    "deleted", false
            );
        }

        deps.deleteById(id);
        return Map.of(
                "message", "Department deleted successfully.",
                "deactivated", false,
                "deleted", true
        );
    }
}
