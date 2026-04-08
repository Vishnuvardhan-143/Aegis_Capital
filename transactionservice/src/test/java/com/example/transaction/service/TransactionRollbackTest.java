package com.example.transaction.service;

import com.example.transaction.dto.TransferRequest;
import com.example.transaction.dto.WithdrawRequest;
import com.example.transaction.model.Transaction;
import com.example.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the Saga rollback mechanism in TransactionService.
 *
 * These tests use Mockito to simulate real-world failure scenarios
 * where the Account Service crashes, returns errors, or is unreachable
 * AFTER money has already been debited from the source account.
 *
 * In each rollback test:
 *   1. The WITHDRAW call succeeds (money leaves source account)
 *   2. The DEPOSIT call fails (money never reaches destination)
 *   3. The rollback fires: a compensating DEPOSIT is sent back to Source
 *   4. The transaction status is saved as FAILED with a refund message
 */
@ExtendWith(MockitoExtension.class)
public class TransactionRollbackTest {

    @Mock
    private TransactionRepository repository;

    @Mock
    private RestTemplate restTemplate;

    private TransactionService service;

    private final String ACCOUNT_URL = "http://localhost:5050";

    @BeforeEach
    void setUp() {
        service = new TransactionService(repository, restTemplate, ACCOUNT_URL);

        // Make repository.save() return the same Transaction object it receives
        // so that tx.getId() etc. work correctly in the service
        when(repository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ─────────────────────────────────────────────────────────────
    // Helper: create a valid TransferRequest
    // ─────────────────────────────────────────────────────────────
    private TransferRequest makeTransferReq(Long from, Long to, BigDecimal amount, String pin) {
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(from);
        req.setToAccountId(to);
        req.setAmount(amount);
        req.setPin(pin);
        return req;
    }

    // Helper: mock PIN verification to return true
    private void mockPinSuccess() {
        when(restTemplate.postForEntity(contains("/verify-pin"), isNull(), eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(true, HttpStatus.OK));
    }

    // ═════════════════════════════════════════════════════════════
    //  TEST 1: Destination account does not exist
    //
    //  Real-world scenario: User types a wrong destination account ID.
    //  The withdraw succeeds, but the deposit call returns a 400/404
    //  error from Account Service → RestClientException is thrown.
    //  Rollback must refund the source account.
    // ═════════════════════════════════════════════════════════════
    @Test
    @DisplayName("ROLLBACK: Destination account not found → money refunded to source")
    void transfer_DestinationNotFound_RollbackRefundsSource() {
        TransferRequest req = makeTransferReq(1L, 999L, BigDecimal.valueOf(500), "1234");
        mockPinSuccess();

        // Withdraw from account 1 → SUCCEEDS (no exception)
        // Deposit to account 999 → FAILS (account not found)
        doNothing()
                .when(restTemplate).put(contains("/1/balance"), isNull()); // withdraw OK
        doThrow(new RestClientException("404 Account not found with id: 999"))
                .when(restTemplate).put(contains("/999/balance"), isNull()); // deposit FAILS

        // After rollback, the service will call PUT /1/balance again (refund)
        // We need to allow that call — it was already set up by doNothing() above

        Transaction result = service.transfer(req);

        // ── Assertions ──
        // Status must show FAILED with refund message, NOT "PENDING" or "SUCCESS"
        assertTrue(result.getStatus().contains("FAILED"),
                "Status should be FAILED but was: " + result.getStatus());
        assertTrue(result.getStatus().contains("refunded"),
                "Status should mention refund but was: " + result.getStatus());

        // Verify the rollback call happened: a DEPOSIT back to account 1
        // Total PUT calls: 1 (withdraw) + 1 (deposit attempt) + 1 (rollback refund) = 3
        verify(restTemplate, times(3)).put(anyString(), isNull());

        System.out.println("✅ TEST 1 PASSED — Status: " + result.getStatus());
    }

    // ═════════════════════════════════════════════════════════════
    //  TEST 2: Account Service crashes mid-transfer
    //
    //  Real-world scenario: The Account Service server crashes or
    //  the network drops right when the deposit call is being made.
    //  Withdraw succeeded, deposit gets a connection error.
    //  Rollback must refund the source account.
    // ═════════════════════════════════════════════════════════════
    @Test
    @DisplayName("ROLLBACK: Account Service crashes during deposit → money refunded to source")
    void transfer_ServiceCrashDuringDeposit_RollbackRefundsSource() {
        TransferRequest req = makeTransferReq(1L, 2L, BigDecimal.valueOf(1000), "1234");
        mockPinSuccess();

        // Withdraw from account 1 → SUCCEEDS
        doNothing()
                .when(restTemplate).put(contains("/1/balance"), isNull());
        // Deposit to account 2 → FAILS (server crash / connection refused)
        doThrow(new RestClientException("Connection refused: Account Service is down"))
                .when(restTemplate).put(contains("/2/balance"), isNull());

        Transaction result = service.transfer(req);

        // ── Assertions ──
        assertTrue(result.getStatus().contains("FAILED"),
                "Status should be FAILED but was: " + result.getStatus());
        assertTrue(result.getStatus().contains("refunded"),
                "Status should mention refund but was: " + result.getStatus());

        // Verify rollback happened
        verify(restTemplate, times(3)).put(anyString(), isNull());

        System.out.println("✅ TEST 2 PASSED — Status: " + result.getStatus());
    }

    // ═════════════════════════════════════════════════════════════
    //  TEST 3: Insufficient balance in source account
    //
    //  Real-world scenario: Source account has less money than the
    //  transfer amount. The WITHDRAW itself fails.
    //  No rollback needed because no money was ever debited.
    // ═════════════════════════════════════════════════════════════
    @Test
    @DisplayName("NO ROLLBACK: Insufficient balance → withdraw fails, no money moved")
    void transfer_InsufficientBalance_NoRollback() {
        TransferRequest req = makeTransferReq(1L, 2L, BigDecimal.valueOf(50000), "1234");
        mockPinSuccess();

        // Withdraw from account 1 → FAILS (insufficient balance)
        doThrow(new RestClientException("400 Insufficient balance"))
                .when(restTemplate).put(contains("/1/balance"), isNull());

        Transaction result = service.transfer(req);

        // ── Assertions ──
        assertTrue(result.getStatus().contains("FAILED"),
                "Status should be FAILED but was: " + result.getStatus());
        assertTrue(result.getStatus().contains("Insufficient balance"),
                "Status should mention insufficient balance but was: " + result.getStatus());

        // Only 1 PUT call (the failed withdraw). NO rollback call.
        verify(restTemplate, times(1)).put(anyString(), isNull());

        System.out.println("✅ TEST 3 PASSED — Status: " + result.getStatus());
    }

    // ═════════════════════════════════════════════════════════════
    //  TEST 4: Invalid PIN
    //
    //  Real-world scenario: User enters wrong PIN.
    //  No REST calls at all — no money moves.
    // ═════════════════════════════════════════════════════════════
    @Test
    @DisplayName("NO ROLLBACK: Invalid PIN → no money movement at all")
    void transfer_InvalidPin_NoMoneyMoved() {
        TransferRequest req = makeTransferReq(1L, 2L, BigDecimal.valueOf(500), "0000");

        // PIN verification returns false
        when(restTemplate.postForEntity(contains("/verify-pin"), isNull(), eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(false, HttpStatus.FORBIDDEN));

        Transaction result = service.transfer(req);

        // ── Assertions ──
        assertEquals("FAILED: INVALID PIN", result.getStatus());

        // No PUT calls at all — no withdraw, no deposit, no rollback
        verify(restTemplate, never()).put(anyString(), isNull());

        System.out.println("✅ TEST 4 PASSED — Status: " + result.getStatus());
    }

    // ═════════════════════════════════════════════════════════════
    //  TEST 5: Successful transfer (happy path)
    //
    //  Both withdraw and deposit succeed. No rollback.
    //  Transaction status should be SUCCESS.
    // ═════════════════════════════════════════════════════════════
    @Test
    @DisplayName("SUCCESS: Normal transfer → no rollback, status is SUCCESS")
    void transfer_Success_NoRollback() {
        TransferRequest req = makeTransferReq(1L, 2L, BigDecimal.valueOf(300), "1234");
        mockPinSuccess();

        // Both calls succeed (no exception thrown)
        doNothing().when(restTemplate).put(anyString(), isNull());

        Transaction result = service.transfer(req);

        // ── Assertions ──
        assertEquals("SUCCESS", result.getStatus());

        // Exactly 2 PUT calls: 1 withdraw + 1 deposit. No rollback.
        verify(restTemplate, times(2)).put(anyString(), isNull());

        System.out.println("✅ TEST 5 PASSED — Status: " + result.getStatus());
    }

    // ═════════════════════════════════════════════════════════════
    //  TEST 6: CRITICAL — Both deposit AND rollback fail
    //
    //  Real-world scenario: Account Service is completely down.
    //  Withdraw succeeded (before crash), deposit fails, AND the
    //  rollback refund also fails. This should be flagged as CRITICAL
    //  requiring manual investigation.
    // ═════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CRITICAL: Both deposit and rollback fail → manual review needed")
    void transfer_DepositAndRollbackBothFail_CriticalStatus() {
        TransferRequest req = makeTransferReq(1L, 2L, BigDecimal.valueOf(750), "1234");
        mockPinSuccess();

        // ALL PUT calls fail — withdraw succeeds first time, then everything crashes
        // First call (withdraw from 1) → succeeds
        // Second call (deposit to 2) → fails
        // Third call (rollback refund to 1) → ALSO fails
        doNothing()                                                               // 1st call: withdraw OK
                .doThrow(new RestClientException("Service unavailable"))           // 2nd call: deposit FAIL
                .doThrow(new RestClientException("Service STILL unavailable"))     // 3rd call: rollback FAIL
                .when(restTemplate).put(anyString(), isNull());

        Transaction result = service.transfer(req);

        // ── Assertions ──
        assertTrue(result.getStatus().contains("CRITICAL"),
                "Status should say CRITICAL but was: " + result.getStatus());
        assertTrue(result.getStatus().contains("Manual review"),
                "Status should mention manual review but was: " + result.getStatus());

        System.out.println("✅ TEST 6 PASSED — Status: " + result.getStatus());
    }

    // ═════════════════════════════════════════════════════════════
    //  TEST 7: Transaction history correctly shows the failed transfer
    //
    //  After a rollback, the transaction should be saved in the DB
    //  and visible in the history for the source account.
    // ═════════════════════════════════════════════════════════════
    @Test
    @DisplayName("HISTORY: Failed transfer is saved and visible in transaction history")
    void transfer_FailedTransfer_SavedToHistory() {
        TransferRequest req = makeTransferReq(1L, 2L, BigDecimal.valueOf(500), "1234");
        mockPinSuccess();

        doNothing()
                .when(restTemplate).put(contains("/1/balance"), isNull());
        doThrow(new RestClientException("Destination error"))
                .when(restTemplate).put(contains("/2/balance"), isNull());

        // Capture what gets saved to the database
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        Transaction result = service.transfer(req);

        // Verify save was called (at least the initial PENDING + final status)
        verify(repository, atLeast(2)).save(captor.capture());

        // Get the LAST saved transaction (the final one)
        List<Transaction> savedTransactions = captor.getAllValues();
        Transaction lastSaved = savedTransactions.get(savedTransactions.size() - 1);

        // ── Assertions ──
        assertEquals("TRANSFER", lastSaved.getType());
        assertEquals(1L, lastSaved.getAccountId());
        assertEquals(2L, lastSaved.getReferenceAccountId());
        assertTrue(lastSaved.getStatus().contains("FAILED"),
                "Saved status should be FAILED but was: " + lastSaved.getStatus());
        assertTrue(lastSaved.getStatus().contains("refunded"),
                "Saved status should mention refund but was: " + lastSaved.getStatus());

        System.out.println("✅ TEST 7 PASSED — Last saved status: " + lastSaved.getStatus());
    }
}
