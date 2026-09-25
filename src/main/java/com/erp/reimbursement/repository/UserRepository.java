package com.erp.reimbursement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.erp.reimbursement.entity.User;
import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findFirstByMobile(String mobile);
    @Query("SELECT u FROM User u WHERE u.employeeId = :employeeId")
    Optional<User> findByEmployeeId(@Param("employeeId") String employeeId);

    @Query("SELECT u FROM User u WHERE u.reportingManager.id = :managerId")
    List<User> findByReportingManagerId(@Param("managerId") Long managerId);
}
