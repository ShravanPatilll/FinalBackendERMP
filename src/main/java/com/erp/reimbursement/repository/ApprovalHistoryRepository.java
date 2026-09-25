package com.erp.reimbursement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.erp.reimbursement.entity.ApprovalHistory;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    boolean existsByPerformedById(Long performedById);
}
