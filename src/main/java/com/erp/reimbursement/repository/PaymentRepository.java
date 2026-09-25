package com.erp.reimbursement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.erp.reimbursement.entity.Payment;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByReimbursementId(Long reimbursementId);
    boolean existsByPaidById(Long paidById);
}
