package com.example.transaction.controller;

import com.example.transaction.model.Transaction;
import com.example.transaction.repository.TransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/admin")
public class AdminController {

    private final TransactionRepository transactionRepository;

    public AdminController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @DeleteMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<Void> deleteTransactionsForAccount(@PathVariable Long accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrReferenceAccountId(accountId);
        transactionRepository.deleteAll(transactions);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<List<Transaction>> getTransactionsForAccount(@PathVariable Long accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrReferenceAccountId(accountId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Record an INTEREST transaction for a given account.
     * Called by Admin Service when applying monthly interest.
     */
    @PostMapping("/accounts/{accountId}/record-interest")
    public ResponseEntity<Void> recordInterestTransaction(@PathVariable Long accountId,
                                                           @RequestParam BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setAccountId(accountId);
        tx.setAmount(amount);
        tx.setType("INTEREST");
        tx.setStatus("SUCCESS");
        transactionRepository.save(tx);
        return ResponseEntity.ok().build();
    }
}
