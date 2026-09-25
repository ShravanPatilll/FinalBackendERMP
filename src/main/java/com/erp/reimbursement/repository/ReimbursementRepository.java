package com.erp.reimbursement.repository;

import com.erp.reimbursement.entity.Reimbursement;
import com.erp.reimbursement.enums.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReimbursementRepository extends JpaRepository<Reimbursement, Long> {

    List<Reimbursement> findByEmployeeIdOrderBySubmittedAtDesc(
            Long employeeId
    );

    List<Reimbursement> findByStatusOrderBySubmittedAtAsc(
            ClaimStatus status
    );

    @Query("SELECT r FROM Reimbursement r WHERE r.employee.reportingManager.id = :managerId ORDER BY r.submittedAt DESC")
    List<Reimbursement> findByEmployeeReportingManagerIdOrderBySubmittedAtDesc(
            @Param("managerId") Long managerId
    );

    @Query("SELECT r FROM Reimbursement r WHERE r.employee.reportingManager.id = :managerId AND r.status = :status ORDER BY r.submittedAt ASC")
    List<Reimbursement> findByEmployeeReportingManagerIdAndStatusOrderBySubmittedAtAsc(
            @Param("managerId") Long managerId,
            @Param("status") ClaimStatus status
    );
}