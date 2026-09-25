package com.erp.reimbursement.entity;

import com.erp.reimbursement.enums.ClaimStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "reimbursements")
@Getter
@Setter
@NoArgsConstructor
public class Reimbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employee_id")
    private User employee;

    @Column(nullable = false)
    private String title;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "reimbursement_categories",
            joinColumns = @JoinColumn(name = "reimbursement_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<ExpenseCategory> categories = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "claim_type_id")
    private ClaimType claimType;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    private String paymentMode;

    @Column(length = 2000)
    private String description;

    private String merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.PENDING_MANAGER_APPROVAL;

    @Column(length = 1000)
    private String rejectionRemark;

    private LocalDateTime submittedAt;

    private LocalDateTime managerActionAt;

    private LocalDateTime financeActionAt;

    private LocalDateTime paidAt;

    @OneToMany(
            mappedBy = "reimbursement",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    private List<ReimbursementAttachment> attachments = new ArrayList<>();

    @OneToMany(
            mappedBy = "reimbursement",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    private List<ApprovalHistory> approvalHistory = new ArrayList<>();

    @PrePersist
    void pre() {

        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }

        if (claimNumber == null) {
            claimNumber = "CLM-" + System.currentTimeMillis();
        }
    }
}