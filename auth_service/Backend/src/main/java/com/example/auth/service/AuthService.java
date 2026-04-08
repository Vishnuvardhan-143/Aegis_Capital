package com.example.auth.service;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.MfaVerificationRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.model.Role;
import com.example.auth.model.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.CustomUserDetailsService;
import com.example.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final MfaService mfaService;
    private final CustomUserDetailsService userDetailsService;
    // In-memory store for password reset tokens (token -> expiry epoch seconds)
    private Map<String, long[]> resetTokens = new ConcurrentHashMap<>();
    private Map<String, String>  resetTokenEmail = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void initAdminData() {
        // Specifically promote the main admin email
        String adminEmail = "vishnu@gmail.com";
        userRepository.findByEmail(adminEmail).ifPresent(user -> {
            boolean hasAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN"));
            if (!hasAdmin) {
                Role adminRole = roleRepository.findByName("ADMIN")
                        .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));
                user.getRoles().add(adminRole);
                userRepository.save(user);
            }
        });
    }

    @jakarta.transaction.Transactional
    public void transferAdminRights(String currentAdminEmail, String newAdminEmail) {
        User currentAdmin = userRepository.findByEmail(currentAdminEmail)
                .orElseThrow(() -> new RuntimeException("Current admin not found"));
        User newAdmin = userRepository.findByEmail(newAdminEmail)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("Admin role not found"));
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(new Role(null, "USER")));

        // Ensure roles are initialized if empty (should not happen in correct flow)
        if (newAdmin.getRoles() == null) newAdmin.setRoles(new HashSet<>());
        if (currentAdmin.getRoles() == null) currentAdmin.setRoles(new HashSet<>());

        // Demote current admin
        currentAdmin.getRoles().removeIf(r -> r.getName().equalsIgnoreCase("ADMIN"));
        if (currentAdmin.getRoles().isEmpty()) {
            currentAdmin.getRoles().add(userRole);
        }

        // Promote new admin
        newAdmin.getRoles().add(adminRole);
        // Ensure new admin doesn't have duplicate roles if that's a concern
        
        userRepository.save(currentAdmin);
        userRepository.save(newAdmin);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }
        if (userRepository.existsByPanNo(request.getPanNo())) {
            throw new RuntimeException("PAN already in use");
        }

        if (request.isAdmin()) {
            if (userRepository.existsByRolesName("ADMIN")) {
                throw new RuntimeException("user with admin access is already there!");
            }
        }

        String roleName = request.isAdmin() ? "ADMIN" : "USER";
        Role userRole = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(null, roleName)));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDob(request.getDob());
        user.setMobileNo(request.getMobileNo());
        user.setPanNo(request.getPanNo());
        user.setMfaSecret(mfaService.generateSecretKey());
        user.setMfaEnabled(true);
        user.setRoles(roles);

        userRepository.save(user);

        return AuthResponse.builder()
                .message("User registered successfully. Scan the QR code with your authenticator app.")
                .secretImageUri(mfaService.generateQrCodeImageUri(user.getMfaSecret(), user.getEmail()))
                .mfaRequired(false)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean userIsAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN"));
        if (request.isAdmin() && !userIsAdmin) {
            throw new RuntimeException("Access Denied: User does not have Admin access");
        }
        if (!request.isAdmin() && userIsAdmin) {
            throw new RuntimeException("Access Denied: Administrators must use Admin Login");
        }

        if (user.isMfaEnabled()) {
            return AuthResponse.builder()
                    .message("MFA required. Please verify using your authenticator app secret.")
                    .mfaRequired(true)
                    .build();
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        
        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        String rolesStr = userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .reduce((a, b) -> a + "," + b).orElse("");
        extraClaims.put("roles", rolesStr);

        String jwtToken = jwtService.generateToken(extraClaims, userDetails);

        return AuthResponse.builder()
                .token(jwtToken)
                .message("Login successful")
                .mfaRequired(false)
                .build();
    }

    public AuthResponse verifyMfa(MfaVerificationRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean userIsAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN"));
        if (request.isAdmin() && !userIsAdmin) {
            throw new RuntimeException("Access Denied: User does not have Admin access");
        }
        if (!request.isAdmin() && userIsAdmin) {
            throw new RuntimeException("Access Denied: Administrators must use Admin Login");
        }

        String code = request.getCode().trim();

        boolean valid = mfaService.verifyCode(user.getMfaSecret(), code);

        if (!valid) {
            throw new RuntimeException("Invalid MFA code");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        String rolesStr = userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .reduce((a, b) -> a + "," + b).orElse("");
        extraClaims.put("roles", rolesStr);

        String jwtToken = jwtService.generateToken(extraClaims, userDetails);

        return AuthResponse.builder()
                .token(jwtToken)
                .message("MFA verification successful")
                .mfaRequired(false)
                .build();
    }

    /**
     * MFA-secured Password Reset: validates the user's TOTP code and updates their password.
     */
    public void resetPasswordMfa(String email, String mfaCode, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with that email"));

        if (!user.isMfaEnabled()) {
            throw new RuntimeException("MFA is not enabled for this account");
        }

        boolean valid = mfaService.verifyCode(user.getMfaSecret(), mfaCode.trim());
        if (!valid) {
            throw new RuntimeException("Invalid Authenticator code");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
