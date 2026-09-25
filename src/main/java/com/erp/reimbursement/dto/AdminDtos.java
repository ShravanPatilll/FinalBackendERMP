package com.erp.reimbursement.dto;

import com.erp.reimbursement.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public final class AdminDtos {
    private AdminDtos() {}

    public record UserRequest(
            String employeeId,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank @Email String email,
            @NotBlank String username,
            String password,
            String mobile,
            String alternateMobile,
            LocalDate dateOfBirth,
            Long departmentId,
            @NotBlank String designation,
            @NotNull Role role,
            Long reportingManagerId,
            Boolean active
    ) {}

    public record MasterRequest(
            @NotBlank String name
    ) {}

    public record DepartmentRequest(
            @NotBlank String name,
            Boolean active
    ) {}
}
