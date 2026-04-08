package com.example.transaction.controller;

import com.example.transaction.dto.DepositRequest;
import com.example.transaction.model.Transaction;
import com.example.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionControllerTest {

    @Mock
    private TransactionService service;

    @InjectMocks
    private TransactionController controller;

    @Test
    void deposit_ReturnsTransaction() {
        DepositRequest req = new DepositRequest();
        req.setAmount(BigDecimal.valueOf(100));

        Transaction tx = new Transaction();
        tx.setStatus("SUCCESS");

        when(service.deposit(req)).thenReturn(tx);

        Transaction result = controller.deposit(req);

        assertEquals("SUCCESS", result.getStatus());
    }

    @Test
    void getHistory_ReturnsList() {
        Transaction tx = new Transaction();
        when(service.getHistory(1L)).thenReturn(Collections.singletonList(tx));

        List<Transaction> history = controller.getHistory(1L);

        assertEquals(1, history.size());
    }
}
