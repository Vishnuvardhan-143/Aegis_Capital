package com.example.auth.service;

import com.example.auth.dto.UserProfileDto;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Service
@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    public UserProfileDto getProfileByEmail(String email) {
        User user = repo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return UserProfileDto.builder()
                .name(safe(user.getName()))
                .email(safe(user.getEmail()))
                .dob(user.getDob() != null ? user.getDob().toString() : "-")
                .mobileNo(safe(user.getMobileNo()))
                .panNo(safe(user.getPanNo()))
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .build();
    }

    private String safe(String v) { return (v == null || v.isBlank()) ? "-" : v; }
}
