package com.erp.reimbursement.service;

import com.erp.reimbursement.entity.User;
import com.erp.reimbursement.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository repo;

    public CurrentUserService(UserRepository repo) {
        this.repo = repo;
    }

    public User get() {

        String authenticatedUser =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName();

        return repo.findByUsernameIgnoreCase(authenticatedUser)
                .orElseGet(() ->
                        repo.findByEmailIgnoreCase(authenticatedUser)
                                .orElseGet(() ->
                                        repo.findByEmployeeId(authenticatedUser)
                                                .orElseThrow(() ->
                                                        new RuntimeException(
                                                                "Authenticated user not found"
                                                        )
                                                )
                                )
                );
    }
}