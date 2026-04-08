package com.example.auth.controller;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_Success() {
        RegisterRequest req = new RegisterRequest();
        AuthResponse mockRes = AuthResponse.builder().message("Success").build();
        when(authService.register(req)).thenReturn(mockRes);

        ResponseEntity<AuthResponse> response = authController.register(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Success", response.getBody().getMessage());
    }

    @Test
    void login_Success() {
        LoginRequest req = new LoginRequest();
        AuthResponse mockRes = AuthResponse.builder().token("token").message("Success").build();
        when(authService.login(req)).thenReturn(mockRes);

        ResponseEntity<AuthResponse> response = authController.login(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getToken());
    }

    @Test
    void login_Failure() {
        LoginRequest req = new LoginRequest();
        when(authService.login(req)).thenThrow(new RuntimeException("Bad credentials"));

        ResponseEntity<AuthResponse> response = authController.login(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bad credentials", response.getBody().getMessage());
    }
}
