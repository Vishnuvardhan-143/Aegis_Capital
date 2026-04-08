package com.example.transaction.service;

import com.example.transaction.dto.DepositRequest;
import com.example.transaction.dto.WithdrawRequest;
import com.example.transaction.model.Transaction;
import com.example.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Edge case tests for TransactionService deposit, withdraw, and history
 * operations. Transfer/rollback tests are in TransactionRollbackTest.
 */
@ExtendWith(MockitoExtension.class)
public class TransactionServiceEdgeCaseTest {

    @Mock private TransactionRepository repository;
    @Mock private RestTemplate restTemplate;

    private TransactionService service;
    private final String ACCOUNT_URL = "http://localhost:5050";

    @BeforeEach
    void setUp() {
        service = new TransactionService(repository, restTemplate, ACCOUNT_URL);
    }

    // Stub repository.save() — call this in tests that trigger save
    private void mockRepoSave() {
        lenient().when(repository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // Helper methods
    private void mockPinSuccess() {
        mockRepoSave();
        when(restTemplate.postForEntity(contains("/verify-pin"), isNull(), eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(true, HttpStatus.OK));
    }

    private void mockPinFailure() {
        mockRepoSave();
        when(restTemplate.postForEntity(contains("/verify-pin"), isNull(), eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(false, HttpStatus.FORBIDDEN));
    }

    private DepositRequest makeDepositReq(Long accountId, BigDecimal amount, String pin) {
        DepositRequest req = new DepositRequest();
        req.setAccountId(accountId);
        req.setAmount(amount);
        req.setPin(pin);
        return req;
    }

    private WithdrawRequest makeWithdrawReq(Long accountId, BigDecimal amount, String pin) {
        WithdrawRequest req = new WithdrawRequest();
        req.setAccountId(accountId);
        req.setAmount(amount);
        req.setPin(pin);
        return req;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Deposit Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deposit Edge Cases")
    class DepositTests {

        @Test
        @DisplayName("Successful deposit → status SUCCESS")
        void success() {
            DepositRequest req = makeDepositReq(1L, BigDecimal.valueOf(500), "1234");
            mockPinSuccess();
            doNothing().when(restTemplate).put(anyString(), isNull());

            Transaction result = service.deposit(req);

            assertEquals("SUCCESS", result.getStatus());
            assertEquals("DEPOSIT", result.getType());
            assertEquals(1L, result.getAccountId());
        }

        @Test
        @DisplayName("Invalid PIN → status FAILED: INVALID PIN, no balance update")
        void invalidPin() {
            DepositRequest req = makeDepositReq(1L, BigDecimal.valueOf(500), "0000");
            mockPinFailure();

            Transaction result = service.deposit(req);

            assertEquals("FAILED: INVALID PIN", result.getStatus());
            verify(restTemplate, never()).put(anyString(), isNull());
        }

        @Test
        @DisplayName("Account Service down → status FAILED with error message")
        void serviceDown() {
            DepositRequest req = makeDepositReq(1L, BigDecimal.valueOf(500), "1234");
            mockPinSuccess();
            doThrow(new RestClientException("Connection refused"))
                    .when(restTemplate).put(anyString(), isNull());

            Transaction result = service.deposit(req);

            assertTrue(result.getStatus().contains("FAILED"));
        }

        @Test
        @DisplayName("PIN verification throws exception → treated as invalid PIN")
        void pinVerificationCrashes() {
            DepositRequest req = makeDepositReq(1L, BigDecimal.valueOf(500), "1234");
            mockRepoSave();
            when(restTemplate.postForEntity(contains("/verify-pin"), isNull(), eq(Boolean.class)))
                    .thenThrow(new RestClientException("Auth service unavailable"));

            Transaction result = service.deposit(req);

            assertEquals("FAILED: INVALID PIN", result.getStatus());
        }

        @Test
        @DisplayName("Transaction saved to DB with PENDING initially")
        void savedWithPendingInitially() {
            DepositRequest req = makeDepositReq(1L, BigDecimal.valueOf(100), "1234");
            mockPinSuccess();

            // Capture the first save (PENDING)
            service.deposit(req);

            verify(repository, atLeast(2)).save(any(Transaction.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Withdraw Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Withdraw Edge Cases")
    class WithdrawTests {

        @Test
        @DisplayName("Successful withdraw → status SUCCESS")
        void success() {
            WithdrawRequest req = makeWithdrawReq(1L, BigDecimal.valueOf(300), "1234");
            mockPinSuccess();
            doNothing().when(restTemplate).put(anyString(), isNull());

            Transaction result = service.withdraw(req);

            assertEquals("SUCCESS", result.getStatus());
            assertEquals("WITHDRAW", result.getType());
        }

        @Test
        @DisplayName("Invalid PIN → status FAILED: INVALID PIN")
        void invalidPin() {
            WithdrawRequest req = makeWithdrawReq(1L, BigDecimal.valueOf(300), "wrong");
            mockPinFailure();

            Transaction result = service.withdraw(req);

            assertEquals("FAILED: INVALID PIN", result.getStatus());
        }

        @Test
        @DisplayName("Insufficient balance → status FAILED: Insufficient balance")
        void insufficientBalance() {
            WithdrawRequest req = makeWithdrawReq(1L, BigDecimal.valueOf(99999), "1234");
            mockPinSuccess();
            doThrow(new RestClientException("400 Insufficient balance"))
                    .when(restTemplate).put(anyString(), isNull());

            Transaction result = service.withdraw(req);

            assertTrue(result.getStatus().contains("FAILED"));
            assertTrue(result.getStatus().contains("Insufficient balance"));
        }

        @Test
        @DisplayName("Account not found → FAILED with error")
        void accountNotFound() {
            WithdrawRequest req = makeWithdrawReq(999L, BigDecimal.valueOf(100), "1234");
            mockPinSuccess();
            doThrow(new RestClientException("404 Account not found with id: 999"))
                    .when(restTemplate).put(anyString(), isNull());

            Transaction result = service.withdraw(req);

            assertTrue(result.getStatus().contains("FAILED"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  History Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transaction History Edge Cases")
    class HistoryTests {

        @Test
        @DisplayName("Account with no transactions → empty list")
        void noTransactions_EmptyList() {
            when(repository.findByAccountIdOrReferenceAccountId(999L)).thenReturn(Collections.emptyList());

            List<Transaction> result = service.getHistory(999L);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Account with multiple transactions → returns all")
        void multipleTransactions_ReturnsAll() {
            when(repository.findByAccountIdOrReferenceAccountId(1L))
                    .thenReturn(List.of(new Transaction(), new Transaction(), new Transaction()));

            List<Transaction> result = service.getHistory(1L);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("History includes transfers where account is destination")
        void historyIncludesIncomingTransfers() {
            Transaction incoming = new Transaction();
            incoming.setType("TRANSFER");
            incoming.setAccountId(2L);           // from account 2
            incoming.setReferenceAccountId(1L);  // to account 1

            when(repository.findByAccountIdOrReferenceAccountId(1L))
                    .thenReturn(List.of(incoming));

            List<Transaction> result = service.getHistory(1L);

            assertEquals(1, result.size());
            assertEquals("TRANSFER", result.get(0).getType());
        }
    }
}
