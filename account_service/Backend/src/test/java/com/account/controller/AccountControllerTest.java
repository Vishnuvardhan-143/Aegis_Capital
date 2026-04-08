package com.account.controller;

import com.account.entity.Account;
import com.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    private Authentication mockAuth;
    private final String TEST_EMAIL = "test@example.com";
    private Account testAccount;

    @BeforeEach
    void setUp() {
        mockAuth = new UsernamePasswordAuthenticationToken(TEST_EMAIL, "password");
        testAccount = Account.builder()
                .id(1L)
                .accno("1234")
                .bankname("Bank")
                .userEmail(TEST_EMAIL)
                .balance(BigDecimal.valueOf(1000))
                .build();
    }

    @Test
    void getUserAccounts_Success() {
        when(accountService.getAccountsByUserEmail(TEST_EMAIL)).thenReturn(Collections.singletonList(testAccount));

        ResponseEntity<List<Account>> response = accountController.getUserAccounts(mockAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getAccountById_Success() {
        when(accountService.getAccountByIdAndEmail(1L, TEST_EMAIL)).thenReturn(Optional.of(testAccount));

        ResponseEntity<Account> response = accountController.getAccountById(1L, mockAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testAccount, response.getBody());
    }

    @Test
    void createAccount_Success() {
        when(accountService.createAccount(TEST_EMAIL, "Bank", "IFSC", BigDecimal.valueOf(1000), "1234"))
                .thenReturn(testAccount);

        ResponseEntity<Account> response = accountController.createAccount("Bank", "IFSC", BigDecimal.valueOf(1000), "1234", mockAuth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getHeaders().getLocation().toString().contains("/api/accounts/1"));
        assertEquals(testAccount, response.getBody());
    }

    @Test
    void updateBalance_Success() {
        when(accountService.updateBalance(1L, BigDecimal.valueOf(500), "DEPOSIT")).thenReturn(testAccount);

        ResponseEntity<Account> response = accountController.updateBalance(1L, BigDecimal.valueOf(500), "DEPOSIT");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testAccount, response.getBody());
    }

    @Test
    void verifyPin_Valid() {
        when(accountService.verifyPin(1L, "1234")).thenReturn(true);

        ResponseEntity<Boolean> response = accountController.verifyPin(1L, "1234");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody());
    }

    @Test
    void verifyPin_Invalid() {
        when(accountService.verifyPin(1L, "wrong")).thenReturn(false);

        ResponseEntity<Boolean> response = accountController.verifyPin(1L, "wrong");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse(response.getBody());
    }
}
