package com.erp.reimbursement.config;

import com.erp.reimbursement.entity.*;
import com.erp.reimbursement.enums.Role;
import com.erp.reimbursement.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(
            JdbcTemplate jdbcTemplate,
            DepartmentRepository d,
            ExpenseCategoryRepository e,
            ClaimTypeRepository c,
            ApprovalLevelRepository a,
            PaymentMethodRepository p,
            UserRepository u,
            PasswordEncoder enc
    ) {
        return args -> {
            // STEP 5: Safe schema alignment for audit columns so string emails can be safely stored
            try {
                jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN updated_by VARCHAR(255) NULL");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN created_by VARCHAR(255) NULL");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN updated_at DATETIME(6) NULL");
            } catch (Exception ignored) {
            }

            // STEP 2: Seed Departments using only existing no-arg constructor and setters
            for (String x : List.of("Operations", "Finance", "Human Resources", "Sales", "Technology", "Administration")) {
                Department entity = d.findByNameIgnoreCase(x).orElse(null);
                if (entity == null) {
                    entity = new Department();
                    entity.setName(x);
                    entity.setActive(true);
                    d.save(entity);
                }
            }

            // STEP 2: Seed Expense Categories using only existing no-arg constructor and setters
            for (String x : List.of("Travel", "Food & Meals", "Accommodation", "Office Supplies", "Communication", "Training & Development", "Client Meeting", "Local Conveyance", "Other")) {
                ExpenseCategory entity = e.findByNameIgnoreCase(x).orElse(null);
                if (entity == null) {
                    entity = new ExpenseCategory();
                    entity.setName(x);
                    entity.setActive(true);
                    e.save(entity);
                }
            }

            // STEP 2: Seed Claim Types using only existing no-arg constructor and setters
            for (String x : List.of("Business Travel", "Medical Reimbursement", "Local Conveyance", "Communication", "Other")) {
                ClaimType entity = c.findByNameIgnoreCase(x).orElse(null);
                if (entity == null) {
                    entity = new ClaimType();
                    entity.setName(x);
                    entity.setActive(true);
                    c.save(entity);
                }
            }

            // STEP 2: Seed Approval Levels using only existing no-arg constructor and setters
            for (String x : List.of("Manager Approval", "Finance Verification", "Final Settlement")) {
                ApprovalLevel entity = a.findByNameIgnoreCase(x).orElse(null);
                if (entity == null) {
                    entity = new ApprovalLevel();
                    entity.setName(x);
                    entity.setActive(true);
                    a.save(entity);
                }
            }

            // STEP 2: Seed Payment Methods using only existing no-arg constructor and setters
            for (String x : List.of("Payroll", "Bank Transfer", "UPI", "Cheque", "Cash", "Other")) {
                PaymentMethod entity = p.findByNameIgnoreCase(x).orElse(null);
                if (entity == null) {
                    entity = new PaymentMethod();
                    entity.setName(x);
                    entity.setActive(true);
                    p.save(entity);
                }
            }

            // STEP 7: Seed Demo Users using employeeId as unique identifier
            seedUser(u, enc, d, "ADM-1001", "System", "Administrator", "admin@example.com", "Admin", "Administration", "Administrator", Role.ADMIN, null);
            seedUser(u, enc, d, "FIN-1001", "Finance", "Officer", "finance@example.com", "Finance", "Finance", "Finance Officer", Role.FINANCE, null);
            seedUser(u, enc, d, "MGR-1001", "Sarah", "Manager", "manager@example.com", "Manager", "Operations", "Operations Manager", Role.MANAGER, null);
            seedUser(u, enc, d, "MGR-1002", "Alex", "Manager", "manager2@example.com", "Manager2", "Technology", "Engineering Manager", Role.MANAGER, null);

            // Resolve manager using findByEmployeeId with string employeeId "MGR-1001"
            User mgr1 = u.findByEmployeeId("MGR-1001").orElse(null);
            User mgr2 = u.findByEmployeeId("MGR-1002").orElse(null);

            // Seed Employees and assign EMP-1001's reporting manager to MGR-1001
            seedUser(u, enc, d, "EMP-1001", "John", "Doe", "user@example.com", "Employee", "Operations", "Senior Associate", Role.EMPLOYEE, mgr1);
            seedUser(u, enc, d, "EMP-1002", "Jane", "Smith", "employee2@example.com", "Employee2", "Operations", "Operations Specialist", Role.EMPLOYEE, mgr1);
            seedUser(u, enc, d, "EMP-1003", "David", "Miller", "employee3@example.com", "Employee3", "Technology", "Software Engineer", Role.EMPLOYEE, mgr2);
            seedUser(u, enc, d, "EMP-1004", "Emily", "Davis", "employee4@example.com", "Employee4", "Technology", "QA Engineer", Role.EMPLOYEE, mgr2);
        };
    }

    private void seedUser(
            UserRepository u,
            PasswordEncoder enc,
            DepartmentRepository d,
            String employeeId,
            String firstName,
            String lastName,
            String email,
            String username,
            String departmentName,
            String designation,
            Role role,
            User reportingManager
    ) {
        // Find existing user by unique string employeeId
        User user = u.findByEmployeeId(employeeId).orElse(null);
        if (user == null && email != null) {
            user = u.findByEmailIgnoreCase(email.trim()).orElse(null);
        }

        boolean isNew = (user == null);
        if (isNew) {
            user = new User();
            user.setEmployeeId(employeeId);
            user.setCreatedBy("system");
            user.setPassword(enc.encode("Password123!"));
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        if (email != null && !email.isBlank()) {
            user.setEmail(email.toLowerCase().trim());
        }
        if (username != null && !username.isBlank()) {
            user.setUsername(username.trim());
        }
        if (user.getPassword() == null) {
            user.setPassword(enc.encode("Password123!"));
        }
        user.setDepartment(d.findByNameIgnoreCase(departmentName).orElse(null));
        user.setDesignation(designation);
        user.setRole(role);
        user.setActive(true);
        if (reportingManager != null) {
            user.setReportingManager(reportingManager);
        }

        u.save(user);
    }
}
