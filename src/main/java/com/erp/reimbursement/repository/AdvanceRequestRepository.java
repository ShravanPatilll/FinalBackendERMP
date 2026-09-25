package com.erp.reimbursement.repository;

import com.erp.reimbursement.entity.AdvanceRequest;
import com.erp.reimbursement.enums.AdvanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvanceRequestRepository extends JpaRepository<AdvanceRequest, Long> {

    List<AdvanceRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<AdvanceRequest> findByEmployeeReportingManagerIdAndStatusOrderByCreatedAtAsc(
            Long managerId,
            AdvanceStatus status
    );

    List<AdvanceRequest> findByStatusOrderByCreatedAtAsc(
            AdvanceStatus status
    );

    List<AdvanceRequest> findByEmployeeReportingManagerIdOrderByCreatedAtDesc(
            Long managerId
    );
}