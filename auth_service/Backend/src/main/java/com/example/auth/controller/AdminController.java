package com.example.auth.controller;

import com.example.auth.dto.UserDTO;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final com.example.auth.service.AuthService authService;
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @PostMapping("/transfer-admin")
    public ResponseEntity<Void> transferAdmin(@RequestParam String currentAdminEmail, @RequestParam String newAdminEmail) {
        authService.transferAdminRights(currentAdminEmail, newAdminEmail);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userRepository.findAll().stream()
                .map(user -> UserDTO.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .dob(user.getDob())
                        .mobileNo(user.getMobileNo())
                        .panNo(user.getPanNo())
                        .mfaEnabled(user.isMfaEnabled())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            restTemplate.delete("http://account-backend:5050/internal/admin/users/" + user.getEmail() + "/accounts");
        } catch (Exception e) {
            log.warn("Failed to delete accounts for user {}: {}", user.getEmail(), e.getMessage());
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
