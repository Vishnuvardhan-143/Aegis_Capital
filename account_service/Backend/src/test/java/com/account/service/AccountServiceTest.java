package com.account.service;

import com.account.entity.Account;
import com.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .id(1L)
                .accno("123456789012")
                .bankname("Test Bank")
                .ifsccode("TEST0001")
                .balance(BigDecimal.valueOf(1000))
                .userEmail(TEST_EMAIL)
                .pin("hashedPin")
                .build();
    }

    @Test
    void createAccount_Success() {
        when(passwordEncoder.encode("1234")).thenReturn("hashedPin");
        when(accountRepository.findByAccno(anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        Account newAccount = accountService.createAccount(TEST_EMAIL, "Test Bank", "TEST0001", BigDecimal.valueOf(1000), "1234");
        
        assertNotNull(newAccount);
        assertEquals(TEST_EMAIL, newAccount.getUserEmail());
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void getBalance_Success() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        BigDecimal balance = accountService.getBalance(1L, TEST_EMAIL);

        assertEquals(BigDecimal.valueOf(1000), balance);
    }

    @Test
    void getBalance_NotFoundOrAccessDenied() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> accountService.getBalance(1L, TEST_EMAIL));
    }

    @Test
    void updateBalance_Deposit() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        Account updated = accountService.updateBalance(1L, BigDecimal.valueOf(500), "DEPOSIT");

        assertEquals(BigDecimal.valueOf(1500), updated.getBalance());
        verify(accountRepository).save(testAccount);
    }

    @Test
    void updateBalance_WithdrawalSuccess() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        Account updated = accountService.updateBalance(1L, BigDecimal.valueOf(500), "WITHDRAW");

        assertEquals(BigDecimal.valueOf(500), updated.getBalance());
    }

    @Test
    void updateBalance_WithdrawalInsufficientFunds() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        assertThrows(RuntimeException.class, () -> 
            accountService.updateBalance(1L, BigDecimal.valueOf(1500), "WITHDRAW")
        );
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void verifyPin_Success() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("1234", "hashedPin")).thenReturn(true);

        boolean result = accountService.verifyPin(1L, "1234");

        assertTrue(result);
    }
}
