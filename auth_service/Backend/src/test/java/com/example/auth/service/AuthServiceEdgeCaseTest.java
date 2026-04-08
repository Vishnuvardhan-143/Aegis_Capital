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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceEdgeCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private MfaService mfaService;
    @Mock private CustomUserDetailsService userDetailsService;

    @InjectMocks private AuthService authService;

    private User testUser;
    private User adminUser;
    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRole = new Role(1L, "USER");
        adminRole = new Role(2L, "ADMIN");

        Set<Role> userRoles = new HashSet<>();
        userRoles.add(userRole);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@aegis.com");
        testUser.setPassword("encodedPassword");
        testUser.setName("Test User");
        testUser.setDob(LocalDate.of(2000, 1, 1));
        testUser.setMobileNo("9876543210");
        testUser.setPanNo("ABCDE1234F");
        testUser.setMfaSecret("TOTP_SECRET_KEY");
        testUser.setMfaEnabled(true);
        testUser.setRoles(userRoles);

        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(adminRole);
        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setEmail("admin@aegis.com");
        adminUser.setPassword("encodedPassword");
        adminUser.setName("Admin");
        adminUser.setRoles(adminRoles);
        adminUser.setMfaEnabled(false);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  register — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Registration Edge Cases")
    class RegisterTests {

        @Test
        @DisplayName("Duplicate email → throws 'Email already in use'")
        void duplicateEmail_Throws() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("user@aegis.com");

            when(userRepository.existsByEmail("user@aegis.com")).thenReturn(true);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(req));
            assertEquals("Email already in use", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate PAN number → throws 'PAN already in use'")
        void duplicatePan_Throws() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("new@test.com");
            req.setPanNo("ABCDE1234F");

            when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
            when(userRepository.existsByPanNo("ABCDE1234F")).thenReturn(true);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(req));
            assertEquals("PAN already in use", ex.getMessage());
        }

        @Test
        @DisplayName("Register as admin when admin already exists → throws")
        void adminAlreadyExists_Throws() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("new@test.com");
            req.setPanNo("NEW12345PAN");
            req.setAdmin(true);

            when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
            when(userRepository.existsByPanNo("NEW12345PAN")).thenReturn(false);
            when(userRepository.existsByRolesName("ADMIN")).thenReturn(true);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(req));
            assertTrue(ex.getMessage().contains("admin access is already there"));
        }

        @Test
        @DisplayName("Successful user registration → MFA secret & QR code generated")
        void successfulRegistration_MfaSetup() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("newuser@test.com");
            req.setPassword("strongpass");
            req.setPanNo("NEWPAN1234X");
            req.setAdmin(false);

            when(userRepository.existsByEmail("newuser@test.com")).thenReturn(false);
            when(userRepository.existsByPanNo("NEWPAN1234X")).thenReturn(false);
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
            when(passwordEncoder.encode("strongpass")).thenReturn("encoded");
            when(mfaService.generateSecretKey()).thenReturn("TOTP_SECRET");
            when(mfaService.generateQrCodeImageUri("TOTP_SECRET", "newuser@test.com")).thenReturn("data:image/png...");

            AuthResponse response = authService.register(req);

            assertNotNull(response.getSecretImageUri());
            assertTrue(response.getMessage().contains("registered successfully"));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Role does not exist in DB → created automatically")
        void roleNotInDb_CreatedAutomatically() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("newuser@test.com");
            req.setPassword("pass");
            req.setPanNo("PAN12345");
            req.setAdmin(false);

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPanNo(anyString())).thenReturn(false);
            when(roleRepository.findByName("USER")).thenReturn(Optional.empty());
            when(roleRepository.save(any(Role.class))).thenReturn(userRole);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(mfaService.generateSecretKey()).thenReturn("SECRET");

            authService.register(req);

            verify(roleRepository).save(any(Role.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  login — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Login Edge Cases")
    class LoginTests {

        @Test
        @DisplayName("Wrong password → AuthenticationManager throws BadCredentialsException")
        void wrongPassword_Throws() {
            LoginRequest req = LoginRequest.builder()
                    .email("user@aegis.com").password("wrong").isAdmin(false).build();

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(BadCredentialsException.class, () -> authService.login(req));
        }

        @Test
        @DisplayName("User not found after authentication → throws RuntimeException")
        void userNotFoundAfterAuth_Throws() {
            LoginRequest req = LoginRequest.builder()
                    .email("ghost@aegis.com").password("pass").isAdmin(false).build();

            when(userRepository.findByEmail("ghost@aegis.com")).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> authService.login(req));
        }

        @Test
        @DisplayName("Non-admin trying admin login → Access Denied")
        void nonAdminTriesAdminLogin_Throws() {
            LoginRequest req = LoginRequest.builder()
                    .email("user@aegis.com").password("pass").isAdmin(true).build();

            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(req));
            assertTrue(ex.getMessage().contains("does not have Admin access"));
        }

        @Test
        @DisplayName("Admin trying user login → Access Denied")
        void adminTriesUserLogin_Throws() {
            LoginRequest req = LoginRequest.builder()
                    .email("admin@aegis.com").password("pass").isAdmin(false).build();

            when(userRepository.findByEmail("admin@aegis.com")).thenReturn(Optional.of(adminUser));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(req));
            assertTrue(ex.getMessage().contains("Administrators must use Admin Login"));
        }

        @Test
        @DisplayName("MFA enabled → returns mfaRequired=true, no token")
        void mfaEnabled_ReturnsNoToken() {
            LoginRequest req = LoginRequest.builder()
                    .email("user@aegis.com").password("pass").isAdmin(false).build();

            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));

            AuthResponse response = authService.login(req);

            assertTrue(response.isMfaRequired());
            assertNull(response.getToken());
        }

        @Test
        @DisplayName("MFA disabled → returns token directly")
        void mfaDisabled_ReturnsToken() {
            testUser.setMfaEnabled(false);
            LoginRequest req = LoginRequest.builder()
                    .email("user@aegis.com").password("pass").isAdmin(false).build();

            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));
            UserDetails userDetails = mock(UserDetails.class);
            when(userDetailsService.loadUserByUsername("user@aegis.com")).thenReturn(userDetails);
            when(jwtService.generateToken(any(), any())).thenReturn("jwt-token-123");

            AuthResponse response = authService.login(req);

            assertFalse(response.isMfaRequired());
            assertEquals("jwt-token-123", response.getToken());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  verifyMfa — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MFA Verification Edge Cases")
    class MfaTests {

        @Test
        @DisplayName("Invalid MFA code → throws RuntimeException")
        void invalidMfaCode_Throws() {
            MfaVerificationRequest req = MfaVerificationRequest.builder()
                    .email("user@aegis.com").code("000000").isAdmin(false).build();

            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));
            when(mfaService.verifyCode("TOTP_SECRET_KEY", "000000")).thenReturn(false);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.verifyMfa(req));
            assertEquals("Invalid MFA code", ex.getMessage());
        }

        @Test
        @DisplayName("Valid MFA code → returns JWT token")
        void validMfaCode_ReturnsToken() {
            MfaVerificationRequest req = MfaVerificationRequest.builder()
                    .email("user@aegis.com").code("123456").isAdmin(false).build();

            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));
            when(mfaService.verifyCode("TOTP_SECRET_KEY", "123456")).thenReturn(true);
            UserDetails userDetails = mock(UserDetails.class);
            when(userDetailsService.loadUserByUsername("user@aegis.com")).thenReturn(userDetails);
            when(jwtService.generateToken(any(), any())).thenReturn("mfa-jwt-token");

            AuthResponse response = authService.verifyMfa(req);

            assertEquals("mfa-jwt-token", response.getToken());
            assertFalse(response.isMfaRequired());
        }

        @Test
        @DisplayName("MFA with leading/trailing spaces in code → trimmed")
        void mfaCodeWithSpaces_Trimmed() {
            MfaVerificationRequest req = MfaVerificationRequest.builder()
                    .email("user@aegis.com").code("  123456  ").isAdmin(false).build();

            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));
            when(mfaService.verifyCode("TOTP_SECRET_KEY", "123456")).thenReturn(true);
            UserDetails userDetails = mock(UserDetails.class);
            when(userDetailsService.loadUserByUsername("user@aegis.com")).thenReturn(userDetails);
            when(jwtService.generateToken(any(), any())).thenReturn("token");

            assertDoesNotThrow(() -> authService.verifyMfa(req));
        }

        @Test
        @DisplayName("Non-admin MFA on admin account → Access Denied")
        void nonAdminMfaOnAdminAccount_Throws() {
            MfaVerificationRequest req = MfaVerificationRequest.builder()
                    .email("admin@aegis.com").code("123456").isAdmin(false).build();

            when(userRepository.findByEmail("admin@aegis.com")).thenReturn(Optional.of(adminUser));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.verifyMfa(req));
            assertTrue(ex.getMessage().contains("Administrators must use Admin Login"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resetPasswordMfa — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Password Reset via MFA Edge Cases")
    class ResetPasswordTests {

        @Test
        @DisplayName("User not found → throws RuntimeException")
        void userNotFound_Throws() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.resetPasswordMfa("ghost@test.com", "123456", "newpass"));
            assertTrue(ex.getMessage().contains("No account found"));
        }

        @Test
        @DisplayName("MFA not enabled → throws RuntimeException")
        void mfaNotEnabled_Throws() {
            testUser.setMfaEnabled(false);
            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.resetPasswordMfa("user@aegis.com", "123456", "newpass"));
            assertTrue(ex.getMessage().contains("MFA is not enabled"));
        }

        @Test
        @DisplayName("Invalid MFA code during password reset → throws")
        void invalidMfaCode_Throws() {
            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));
            when(mfaService.verifyCode("TOTP_SECRET_KEY", "000000")).thenReturn(false);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.resetPasswordMfa("user@aegis.com", "000000", "newpass"));
            assertTrue(ex.getMessage().contains("Invalid Authenticator code"));
        }

        @Test
        @DisplayName("Successful password reset → password encoded and saved")
        void successfulReset() {
            when(userRepository.findByEmail("user@aegis.com")).thenReturn(Optional.of(testUser));
            when(mfaService.verifyCode("TOTP_SECRET_KEY", "123456")).thenReturn(true);
            when(passwordEncoder.encode("newpass")).thenReturn("$encoded$newpass");

            assertDoesNotThrow(() -> authService.resetPasswordMfa("user@aegis.com", "123456", "newpass"));

            verify(userRepository).save(testUser);
            assertEquals("$encoded$newpass", testUser.getPassword());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  transferAdminRights — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Admin Transfer Edge Cases")
    class TransferAdminTests {

        @Test
        @DisplayName("Current admin not found → throws")
        void currentAdminNotFound_Throws() {
            when(userRepository.findByEmail("ghost@admin.com")).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> authService.transferAdminRights("ghost@admin.com", "new@admin.com"));
        }

        @Test
        @DisplayName("Target user not found → throws")
        void targetUserNotFound_Throws() {
            when(userRepository.findByEmail("admin@aegis.com")).thenReturn(Optional.of(adminUser));
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> authService.transferAdminRights("admin@aegis.com", "ghost@test.com"));
        }
    }
}
