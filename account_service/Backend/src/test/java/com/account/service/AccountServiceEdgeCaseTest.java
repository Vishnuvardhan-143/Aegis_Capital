package com.account.service;

import com.account.entity.Account;
import com.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceEdgeCaseTest {

    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AccountService accountService;

    private Account testAccount;
    private final String TEST_EMAIL = "user@aegis.com";

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .id(1L)
                .accno("ACC123456789")
                .bankname("Aegis Bank")
                .ifsccode("AEGS0001")
                .balance(BigDecimal.valueOf(5000))
                .userEmail(TEST_EMAIL)
                .pin("$2a$10$hashedPinValue")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  updateBalance — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateBalance Edge Cases")
    class UpdateBalanceTests {

        @Test
        @DisplayName("Withdraw exact balance → balance becomes zero")
        void withdraw_ExactBalance_BecomesZero() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any())).thenReturn(testAccount);

            Account result = accountService.updateBalance(1L, BigDecimal.valueOf(5000), "WITHDRAW");

            assertEquals(BigDecimal.ZERO.compareTo(result.getBalance()), 0,
                    "Balance should be exactly 0 after withdrawing full amount");
        }

        @Test
        @DisplayName("Withdraw more than balance → throws Insufficient balance")
        void withdraw_MoreThanBalance_Throws() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> accountService.updateBalance(1L, BigDecimal.valueOf(5001), "WITHDRAW"));
            assertTrue(ex.getMessage().contains("Insufficient balance"));
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Withdraw zero amount → balance unchanged")
        void withdraw_ZeroAmount_BalanceUnchanged() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any())).thenReturn(testAccount);

            Account result = accountService.updateBalance(1L, BigDecimal.ZERO, "WITHDRAW");

            assertEquals(BigDecimal.valueOf(5000), result.getBalance());
        }

        @Test
        @DisplayName("Deposit zero amount → balance unchanged")
        void deposit_ZeroAmount_BalanceUnchanged() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any())).thenReturn(testAccount);

            Account result = accountService.updateBalance(1L, BigDecimal.ZERO, "DEPOSIT");

            assertEquals(BigDecimal.valueOf(5000), result.getBalance());
        }

        @Test
        @DisplayName("Deposit very large amount → no overflow")
        void deposit_VeryLargeAmount_NoOverflow() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any())).thenReturn(testAccount);

            BigDecimal largeAmount = new BigDecimal("99999999999.99");
            Account result = accountService.updateBalance(1L, largeAmount, "DEPOSIT");

            assertEquals(BigDecimal.valueOf(5000).add(largeAmount), result.getBalance());
        }

        @Test
        @DisplayName("Invalid type → throws RuntimeException")
        void invalidType_Throws() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> accountService.updateBalance(1L, BigDecimal.TEN, "TRANSFER"));
            assertTrue(ex.getMessage().contains("Invalid type"));
        }

        @Test
        @DisplayName("Case-insensitive type: 'deposit' works same as 'DEPOSIT'")
        void caseInsensitiveType_Deposit() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any())).thenReturn(testAccount);

            Account result = accountService.updateBalance(1L, BigDecimal.valueOf(100), "deposit");

            assertEquals(BigDecimal.valueOf(5100), result.getBalance());
        }

        @Test
        @DisplayName("Account not found → throws RuntimeException")
        void accountNotFound_Throws() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> accountService.updateBalance(999L, BigDecimal.TEN, "DEPOSIT"));
            assertTrue(ex.getMessage().contains("Account not found"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  verifyPin — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("verifyPin Edge Cases")
    class VerifyPinTests {

        @Test
        @DisplayName("Correct PIN → returns true")
        void correctPin_ReturnsTrue() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(passwordEncoder.matches("1234", testAccount.getPin())).thenReturn(true);

            assertTrue(accountService.verifyPin(1L, "1234"));
        }

        @Test
        @DisplayName("Wrong PIN → returns false")
        void wrongPin_ReturnsFalse() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(passwordEncoder.matches("0000", testAccount.getPin())).thenReturn(false);

            assertFalse(accountService.verifyPin(1L, "0000"));
        }

        @Test
        @DisplayName("Account not found → returns false")
        void accountNotFound_ReturnsFalse() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertFalse(accountService.verifyPin(999L, "1234"));
        }

        @Test
        @DisplayName("Null PIN → returns false")
        void nullPin_ReturnsFalse() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(passwordEncoder.matches(isNull(), anyString())).thenReturn(false);

            assertFalse(accountService.verifyPin(1L, null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  resetPin — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resetPin Edge Cases")
    class ResetPinTests {

        @Test
        @DisplayName("Account not found → throws RuntimeException")
        void accountNotFound_Throws() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> accountService.resetPin(999L, TEST_EMAIL, "old", "new"));
            assertTrue(ex.getMessage().contains("Account not found"));
        }

        @Test
        @DisplayName("Wrong email (access denied) → throws RuntimeException")
        void wrongEmail_Throws() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> accountService.resetPin(1L, "hacker@evil.com", "old", "new"));
            assertTrue(ex.getMessage().contains("Access denied"));
        }

        @Test
        @DisplayName("Incorrect old PIN → throws RuntimeException")
        void incorrectOldPin_Throws() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(passwordEncoder.matches("wrongOld", testAccount.getPin())).thenReturn(false);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> accountService.resetPin(1L, TEST_EMAIL, "wrongOld", "newPin"));
            assertTrue(ex.getMessage().contains("Current PIN is incorrect"));
        }

        @Test
        @DisplayName("Successful PIN reset → updatePin called with encoded PIN")
        void successfulReset() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(passwordEncoder.matches("oldPin", testAccount.getPin())).thenReturn(true);
            when(passwordEncoder.encode("newPin")).thenReturn("$encoded$newPin");
            when(accountRepository.updatePin(1L, TEST_EMAIL, "$encoded$newPin")).thenReturn(1);

            assertDoesNotThrow(() -> accountService.resetPin(1L, TEST_EMAIL, "oldPin", "newPin"));
            verify(accountRepository).updatePin(1L, TEST_EMAIL, "$encoded$newPin");
        }

        @Test
        @DisplayName("DB update returns 0 rows → throws RuntimeException")
        void dbUpdateFails_Throws() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(passwordEncoder.matches("oldPin", testAccount.getPin())).thenReturn(true);
            when(passwordEncoder.encode("newPin")).thenReturn("$encoded$newPin");
            when(accountRepository.updatePin(1L, TEST_EMAIL, "$encoded$newPin")).thenReturn(0);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> accountService.resetPin(1L, TEST_EMAIL, "oldPin", "newPin"));
            assertTrue(ex.getMessage().contains("Failed to update PIN"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  createAccount — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createAccount Edge Cases")
    class CreateAccountTests {

        @Test
        @DisplayName("Null initial balance → defaults to BigDecimal.ZERO")
        void nullInitialBalance_DefaultsToZero() {
            when(passwordEncoder.encode("1234")).thenReturn("hashed");
            when(accountRepository.findByAccno(anyString())).thenReturn(Optional.empty());
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            Account result = accountService.createAccount(TEST_EMAIL, "Bank", "IFSC", null, "1234");

            assertEquals(BigDecimal.ZERO, result.getBalance());
        }

        @Test
        @DisplayName("Account number collision resolved by retry")
        void accountNumberCollision_Retries() {
            when(passwordEncoder.encode("1234")).thenReturn("hashed");
            // First attempt: accno already exists; second attempt: unique
            when(accountRepository.findByAccno(anyString()))
                    .thenReturn(Optional.of(testAccount))   // 1st attempt collision
                    .thenReturn(Optional.empty());           // 2nd attempt unique
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            Account result = accountService.createAccount(TEST_EMAIL, "Bank", "IFSC", BigDecimal.TEN, "1234");

            assertNotNull(result);
            verify(accountRepository, atLeast(2)).findByAccno(anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  getAccountByIdAndEmail — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAccountByIdAndEmail Edge Cases")
    class GetAccountByIdAndEmailTests {

        @Test
        @DisplayName("Correct ID and email → returns account")
        void correctIdAndEmail_ReturnsAccount() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Optional<Account> result = accountService.getAccountByIdAndEmail(1L, TEST_EMAIL);

            assertTrue(result.isPresent());
            assertEquals(TEST_EMAIL, result.get().getUserEmail());
        }

        @Test
        @DisplayName("Correct ID but wrong email → returns empty (access denied)")
        void correctIdWrongEmail_ReturnsEmpty() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Optional<Account> result = accountService.getAccountByIdAndEmail(1L, "other@user.com");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Account does not exist → returns empty")
        void accountDoesNotExist_ReturnsEmpty() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Account> result = accountService.getAccountByIdAndEmail(999L, TEST_EMAIL);

            assertTrue(result.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  getAccountsByUserEmail — Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("User with no accounts → returns empty list")
    void noAccounts_ReturnsEmptyList() {
        when(accountRepository.findByUserEmail("nobody@test.com")).thenReturn(Collections.emptyList());

        List<Account> result = accountService.getAccountsByUserEmail("nobody@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("User with multiple accounts → returns all")
    void multipleAccounts_ReturnsAll() {
        Account acc2 = Account.builder().id(2L).userEmail(TEST_EMAIL).balance(BigDecimal.valueOf(2000)).build();
        when(accountRepository.findByUserEmail(TEST_EMAIL)).thenReturn(List.of(testAccount, acc2));

        List<Account> result = accountService.getAccountsByUserEmail(TEST_EMAIL);

        assertEquals(2, result.size());
    }
}
