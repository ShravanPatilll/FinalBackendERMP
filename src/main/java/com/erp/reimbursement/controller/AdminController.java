package com.erp.reimbursement.controller;

import com.erp.reimbursement.dto.AdminDtos.*;
import com.erp.reimbursement.entity.Department;
import com.erp.reimbursement.entity.User;
import com.erp.reimbursement.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService s;

    public AdminController(AdminService s) {
        this.s = s;
    }

    @GetMapping("/users")
    public List<User> users() {
        return s.users();
    }

    @GetMapping("/managers")
    public List<User> managers() {
        return s.managers();
    }

    @PostMapping("/users")
    public User create(@Valid @RequestBody UserRequest r) {
        return s.create(r);
    }

    @PutMapping("/users/{id}")
    public User update(@PathVariable Long id, @Valid @RequestBody UserRequest r) {
        return s.update(id, r);
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        return s.delete(id);
    }

    @GetMapping("/departments")
    public List<Department> departments() {
        return s.departments();
    }

    @PostMapping("/departments")
    public Department createDepartment(@Valid @RequestBody DepartmentRequest r) {
        return s.createDepartment(r);
    }

    @PutMapping("/departments/{id}")
    public Department updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest r) {
        return s.updateDepartment(id, r);
    }

    @DeleteMapping("/departments/{id}")
    public Map<String, Object> deleteDepartment(@PathVariable Long id) {
        return s.deleteDepartment(id);
    }
}
