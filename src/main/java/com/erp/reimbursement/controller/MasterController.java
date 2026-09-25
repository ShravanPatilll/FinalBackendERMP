package com.erp.reimbursement.controller;

import com.erp.reimbursement.entity.*;
import com.erp.reimbursement.repository.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/masters")
public class MasterController {

    private final DepartmentRepository d;
    private final ExpenseCategoryRepository e;
    private final ClaimTypeRepository ct;
    private final ApprovalLevelRepository al;
    private final PaymentMethodRepository pm;

    public MasterController(
            DepartmentRepository d,
            ExpenseCategoryRepository e,
            ClaimTypeRepository ct,
            ApprovalLevelRepository al,
            PaymentMethodRepository pm
    ) {
        this.d = d;
        this.e = e;
        this.ct = ct;
        this.al = al;
        this.pm = pm;
    }

    @GetMapping("/public")
    public Map<String, Object> publicMasters() {
        return map();
    }

    @GetMapping
    public Map<String, Object> masters() {
        return map();
    }

    private Map<String, Object> map() {
        return Map.of(
                "departments", d.findAllByActiveTrueOrderByNameAsc(),
                "expenseCategories", e.findAllByActiveTrueOrderByNameAsc(),
                "claimTypes", ct.findAllByActiveTrueOrderByNameAsc(),
                "approvalLevels", al.findAllByActiveTrueOrderByNameAsc(),
                "paymentMethods", pm.findAllByActiveTrueOrderByNameAsc()
        );
    }

    @PostMapping("/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public Department addD(@RequestBody Map<String, String> x) {
        Department item = new Department();
        item.setName(x.get("name"));
        item.setActive(true);
        return d.save(item);
    }

    @PostMapping("/expense-categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ExpenseCategory addE(@RequestBody Map<String, String> x) {
        ExpenseCategory item = new ExpenseCategory();
        item.setName(x.get("name"));
        item.setActive(true);
        return e.save(item);
    }

    @PostMapping("/claim-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ClaimType addC(@RequestBody Map<String, String> x) {
        ClaimType item = new ClaimType();
        item.setName(x.get("name"));
        item.setActive(true);
        return ct.save(item);
    }

    @PostMapping("/approval-levels")
    @PreAuthorize("hasRole('ADMIN')")
    public ApprovalLevel addA(@RequestBody Map<String, String> x) {
        ApprovalLevel item = new ApprovalLevel();
        item.setName(x.get("name"));
        item.setActive(true);
        return al.save(item);
    }

    @PostMapping("/payment-methods")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentMethod addP(@RequestBody Map<String, String> x) {
        PaymentMethod item = new PaymentMethod();
        item.setName(x.get("name"));
        item.setActive(true);
        return pm.save(item);
    }
}
