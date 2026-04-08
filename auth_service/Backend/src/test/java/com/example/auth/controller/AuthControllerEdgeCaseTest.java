package com.example.auth.controller;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.MfaVerificationRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerEdgeCaseTest {

    @Mock private AuthService authService;
    @InjectMocks private AuthController authController;

    // ═══════════════════════════════════════════════════════════════════
    //  Register Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Register Endpoint Edge Cases")
    class RegisterTests {

        @Test
        @DisplayName("Service throws → 400 with error message")
        void serviceThrows_Returns400() {
            RegisterRequest req = new RegisterRequest();
            when(authService.register(req)).thenThrow(new RuntimeException("Email already in use"));

            ResponseEntity<AuthResponse> response = authController.register(req);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Email already in use", response.getBody().getMessage());
            assertFalse(response.getBody().isMfaRequired());
        }

        @Test
        @DisplayName("Successful register → 200 OK")
        void success_Returns200() {
            RegisterRequest req = new RegisterRequest();
            AuthResponse mockRes = AuthResponse.builder()
                    .message("User registered successfully. Scan the QR code with your authenticator app.")
                    .secretImageUri("data:image/png...")
                    .build();
            when(authService.register(req)).thenReturn(mockRes);

            ResponseEntity<AuthResponse> response = authController.register(req);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody().getSecretImageUri());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Login Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Login Endpoint Edge Cases")
    class LoginTests {

        @Test
        @DisplayName("Bad credentials → 400 with message")
        void badCredentials_Returns400() {
            LoginRequest req = new LoginRequest();
            when(authService.login(req)).thenThrow(new RuntimeException("Bad credentials"));

            ResponseEntity<AuthResponse> response = authController.login(req);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Bad credentials", response.getBody().getMessage());
        }

        @Test
        @DisplayName("MFA required → 200 with mfaRequired=true")
        void mfaRequired_Returns200() {
            LoginRequest req = new LoginRequest();
            AuthResponse mockRes = AuthResponse.builder()
                    .message("MFA required")
                    .mfaRequired(true)
                    .build();
            when(authService.login(req)).thenReturn(mockRes);

            ResponseEntity<AuthResponse> response = authController.login(req);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isMfaRequired());
            assertNull(response.getBody().getToken());
        }

        @Test
        @DisplayName("Non-admin tries admin login → 400")
        void nonAdminTriesAdminLogin_Returns400() {
            LoginRequest req = LoginRequest.builder().email("user@test.com").isAdmin(true).build();
            when(authService.login(req)).thenThrow(new RuntimeException("Access Denied: User does not have Admin access"));

            ResponseEntity<AuthResponse> response = authController.login(req);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().getMessage().contains("Access Denied"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MFA Verification Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MFA Verification Endpoint Edge Cases")
    class MfaTests {

        @Test
        @DisplayName("Invalid MFA code → 400")
        void invalidCode_Returns400() {
            MfaVerificationRequest req = MfaVerificationRequest.builder()
                    .email("user@test.com").code("000000").build();
            when(authService.verifyMfa(req)).thenThrow(new RuntimeException("Invalid MFA code"));

            ResponseEntity<AuthResponse> response = authController.verifyMfa(req);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Invalid MFA code", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Valid MFA code → returns token")
        void validCode_ReturnsToken() {
            MfaVerificationRequest req = MfaVerificationRequest.builder()
                    .email("user@test.com").code("123456").build();
            AuthResponse mockRes = AuthResponse.builder()
                    .token("jwt-token-mfa")
                    .message("MFA verification successful")
                    .build();
            when(authService.verifyMfa(req)).thenReturn(mockRes);

            ResponseEntity<AuthResponse> response = authController.verifyMfa(req);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("jwt-token-mfa", response.getBody().getToken());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Password Reset via MFA Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Password Reset Endpoint Edge Cases")
    class ResetPasswordTests {

        @Test
        @DisplayName("Missing email → 400")
        void missingEmail_Returns400() {
            Map<String, String> body = Map.of("mfaCode", "123456", "newPassword", "newpass123");

            ResponseEntity<Map<String, String>> response = authController.resetPasswordMfa(body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().get("message").contains("Email is required"));
        }

        @Test
        @DisplayName("Blank email → 400")
        void blankEmail_Returns400() {
            Map<String, String> body = Map.of("email", "  ", "mfaCode", "123456", "newPassword", "newpass123");

            ResponseEntity<Map<String, String>> response = authController.resetPasswordMfa(body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().get("message").contains("Email is required"));
        }

        @Test
        @DisplayName("Missing MFA code → 400")
        void missingMfaCode_Returns400() {
            Map<String, String> body = Map.of("email", "user@test.com", "newPassword", "newpass123");

            ResponseEntity<Map<String, String>> response = authController.resetPasswordMfa(body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().get("message").contains("Authenticator code is required"));
        }

        @Test
        @DisplayName("Password too short (<8 chars) → 400")
        void shortPassword_Returns400() {
            Map<String, String> body = Map.of("email", "user@test.com", "mfaCode", "123456", "newPassword", "short");

            ResponseEntity<Map<String, String>> response = authController.resetPasswordMfa(body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().get("message").contains("at least 8 characters"));
        }

        @Test
        @DisplayName("Successful password reset → 200 with success message")
        void successfulReset_Returns200() {
            Map<String, String> body = Map.of("email", "user@test.com", "mfaCode", "123456", "newPassword", "newpass1234");
            doNothing().when(authService).resetPasswordMfa("user@test.com", "123456", "newpass1234");

            ResponseEntity<Map<String, String>> response = authController.resetPasswordMfa(body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().get("message").contains("Password reset successfully"));
        }

        @Test
        @DisplayName("Service throws → 400 with error message")
        void serviceThrows_Returns400() {
            Map<String, String> body = Map.of("email", "user@test.com", "mfaCode", "000000", "newPassword", "newpass1234");
            doThrow(new RuntimeException("Invalid Authenticator code"))
                    .when(authService).resetPasswordMfa("user@test.com", "000000", "newpass1234");

            ResponseEntity<Map<String, String>> response = authController.resetPasswordMfa(body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Invalid Authenticator code", response.getBody().get("message"));
        }
    }
}
