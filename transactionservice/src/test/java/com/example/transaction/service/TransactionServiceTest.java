package com.example.transaction.service;

import com.example.transaction.dto.DepositRequest;
import com.example.transaction.dto.TransferRequest;
import com.example.transaction.model.Transaction;
import com.example.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository repository;

    @Mock
    private RestTemplate restTemplate;

    private TransactionService service;

    private final String ACCOUNT_URL = "http://account-backend:5050";

    @BeforeEach
    void setUp() {
        service = new TransactionService(repository, restTemplate, ACCOUNT_URL);
    }

    @Test
    void deposit_Success() {
        DepositRequest req = new DepositRequest();
        req.setAccountId(1L);
        req.setAmount(BigDecimal.valueOf(500));
        req.setPin("1234");

        Transaction savedTx = new Transaction();
        savedTx.setStatus("SUCCESS");

        when(restTemplate.postForEntity(contains("/verify-pin"), isNull(), eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(true, HttpStatus.OK));
        when(repository.save(any(Transaction.class))).thenReturn(savedTx);

        Transaction result = service.deposit(req);

        assertEquals("SUCCESS", result.getStatus());
        verify(restTemplate).put(contains("/balance?amount=500"), isNull());
    }

    @Test
    void deposit_InvalidPin() {
        DepositRequest req = new DepositRequest();
        req.setAccountId(1L);
        req.setAmount(BigDecimal.valueOf(500));
        req.setPin("0000");

        Transaction savedTx = new Transaction();
        savedTx.setStatus("FAILED: INVALID PIN");

        when(restTemplate.postForEntity(contains("/verify-pin"), isNull(), eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(false, HttpStatus.FORBIDDEN));
        when(repository.save(any(Transaction.class))).thenReturn(savedTx);

        Transaction result = service.deposit(req);

        assertEquals("FAILED: INVALID PIN", result.getStatus());
        verify(restTemplate, never()).put(anyString(), isNull());
    }

    @Test
    void transfer_Success() {
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(1L);
        req.setToAccountId(2L);
        req.setAmount(BigDecimal.valueOf(200));
        req.setPin("1234");

        Transaction savedTx = new Transaction();
        savedTx.setStatus("SUCCESS");

        when(restTemplate.postForEntity(contains("/verify-pin"), isNull(), eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(true, HttpStatus.OK));
        when(repository.save(any(Transaction.class))).thenReturn(savedTx);

        Transaction result = service.transfer(req);

        assertEquals("SUCCESS", result.getStatus());
        // Withdraw from 1
        verify(restTemplate).put(contains("/1/balance?amount=200&type=WITHDRAW"), isNull());
        // Deposit to 2
        verify(restTemplate).put(contains("/2/balance?amount=200&type=DEPOSIT"), isNull());
    }

    @Test
    void getHistory_Success() {
        when(repository.findByAccountIdOrReferenceAccountId(1L)).thenReturn(Collections.singletonList(new Transaction()));

        List<Transaction> result = service.getHistory(1L);

        assertEquals(1, result.size());
    }
}
