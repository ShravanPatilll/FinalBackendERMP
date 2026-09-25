package com.erp.reimbursement.security;

import com.erp.reimbursement.entity.User;
import com.erp.reimbursement.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository repo;

    public CustomUserDetailsService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        User u = repo.findByEmailIgnoreCase(login)
                .orElseGet(() -> repo.findByUsernameIgnoreCase(login)
                        .orElseGet(() -> repo.findByEmployeeId(login)
                                .orElseGet(() -> repo.findFirstByMobile(login)
                                        .orElseThrow(() -> new UsernameNotFoundException("User not found"))
                                )
                        )
                );

        return org.springframework.security.core.userdetails.User
                .withUsername(u.getUsername())
                .password(u.getPassword())
                .roles(u.getRole().name())
                .disabled(!u.isActive())
                .build();
    }
}
