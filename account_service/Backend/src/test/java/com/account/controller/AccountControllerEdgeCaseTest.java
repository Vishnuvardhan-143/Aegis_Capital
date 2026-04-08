package com.account.controller;

import com.account.entity.Account;
import com.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountControllerEdgeCaseTest {

    @Mock private AccountService accountService;
    @InjectMocks private AccountController controller;

    private final String TEST_EMAIL = "user@aegis.com";
    private Authentication mockAuth;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        mockAuth = new UsernamePasswordAuthenticationToken(TEST_EMAIL, "password");
        testAccount = Account.builder()
                .id(1L)
                .accno("ACC123")
                .bankname("Aegis Bank")
                .userEmail(TEST_EMAIL)
                .balance(BigDecimal.valueOf(5000))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Authentication Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Authentication Edge Cases")
    class AuthenticationTests {

        @Test
        @DisplayName("Null authentication → throws 401 Unauthorized")
        void nullAuth_Throws401() {
            assertThrows(ResponseStatusException.class,
                    () -> controller.getUserAccounts(null));
        }

        @Test
        @DisplayName("Authentication with blank name → throws 401")
        void blankAuthName_Throws401() {
            Authentication blankAuth = new UsernamePasswordAuthenticationToken("", "password");

            assertThrows(ResponseStatusException.class,
                    () -> controller.getUserAccounts(blankAuth));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  getAccountById Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAccountById Edge Cases")
    class GetAccountByIdTests {

        @Test
        @DisplayName("Account exists and belongs to user → 200 OK")
        void accountExists_Returns200() {
            when(accountService.getAccountByIdAndEmail(1L, TEST_EMAIL)).thenReturn(Optional.of(testAccount));

            ResponseEntity<Account> response = controller.getAccountById(1L, mockAuth);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(testAccount, response.getBody());
        }

        @Test
        @DisplayName("Account does not exist → 404 Not Found")
        void accountNotFound_Returns404() {
            when(accountService.getAccountByIdAndEmail(999L, TEST_EMAIL)).thenReturn(Optional.empty());

            ResponseEntity<Account> response = controller.getAccountById(999L, mockAuth);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        @DisplayName("Account belongs to different user → 404 (filtered out by service)")
        void accountBelongsToDifferentUser_Returns404() {
            when(accountService.getAccountByIdAndEmail(1L, TEST_EMAIL)).thenReturn(Optional.empty());

            ResponseEntity<Account> response = controller.getAccountById(1L, mockAuth);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  getAccountBalance Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAccountBalance Edge Cases")
    class GetBalanceTests {

        @Test
        @DisplayName("Valid account → returns balance")
        void validAccount_ReturnsBalance() {
            when(accountService.getAccountByIdAndEmail(1L, TEST_EMAIL)).thenReturn(Optional.of(testAccount));

            ResponseEntity<BigDecimal> response = controller.getAccountBalance(1L, mockAuth);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(BigDecimal.valueOf(5000), response.getBody());
        }

        @Test
        @DisplayName("Account not found → 404")
        void accountNotFound_Returns404() {
            when(accountService.getAccountByIdAndEmail(999L, TEST_EMAIL)).thenReturn(Optional.empty());

            ResponseEntity<BigDecimal> response = controller.getAccountBalance(999L, mockAuth);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  updateBalance (internal) Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateBalance (Internal) Edge Cases")
    class UpdateBalanceTests {

        @Test
        @DisplayName("Successful deposit → 200 OK")
        void deposit_Returns200() {
            when(accountService.updateBalance(1L, BigDecimal.valueOf(500), "DEPOSIT")).thenReturn(testAccount);

            ResponseEntity<Account> response = controller.updateBalance(1L, BigDecimal.valueOf(500), "DEPOSIT");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("Service throws RuntimeException → 400 Bad Request")
        void serviceFails_Returns400() {
            when(accountService.updateBalance(1L, BigDecimal.valueOf(99999), "WITHDRAW"))
                    .thenThrow(new RuntimeException("Insufficient balance"));

            ResponseEntity<Account> response = controller.updateBalance(1L, BigDecimal.valueOf(99999), "WITHDRAW");

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resetPin Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resetPin Edge Cases")
    class ResetPinTests {

        @Test
        @DisplayName("Successful reset → returns success message")
        void successfulReset_Returns200() {
            doNothing().when(accountService).resetPin(1L, TEST_EMAIL, "old", "new");

            ResponseEntity<String> response = controller.resetPin(1L, "old", "new", mockAuth);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("PIN updated successfully", response.getBody());
        }

        @Test
        @DisplayName("Incorrect old PIN → 400 with error message")
        void incorrectOldPin_Returns400() {
            doThrow(new RuntimeException("Current PIN is incorrect"))
                    .when(accountService).resetPin(1L, TEST_EMAIL, "wrong", "new");

            ResponseEntity<String> response = controller.resetPin(1L, "wrong", "new", mockAuth);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().contains("Current PIN is incorrect"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Internal Endpoints
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Internal Endpoints Edge Cases")
    class InternalEndpointTests {

        @Test
        @DisplayName("Get account by accno → found")
        void getByAccno_Found() {
            when(accountService.findByAccno("ACC123")).thenReturn(Optional.of(testAccount));

            ResponseEntity<Account> response = controller.getAccountByAccno("ACC123");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("Get account by accno → not found")
        void getByAccno_NotFound() {
            when(accountService.findByAccno("INVALID")).thenReturn(Optional.empty());

            ResponseEntity<Account> response = controller.getAccountByAccno("INVALID");

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }
}
