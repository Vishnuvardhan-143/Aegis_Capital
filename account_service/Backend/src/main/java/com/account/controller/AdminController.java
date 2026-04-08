package com.account.controller;

import com.account.dto.AccountDTO;
import com.account.entity.Account;
import com.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AccountRepository accountRepository;
    private final RestTemplate restTemplate;
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @org.springframework.beans.factory.annotation.Value("${transaction-service.url:http://transaction-backend:5005}")
    private String transactionServiceUrl;

    private AccountDTO convertToDto(Account account) {
        return AccountDTO.builder()
                .id(account.getId())
                .accno(account.getAccno())
                .ifsccode(account.getIfsccode())
                .bankname(account.getBankname())
                .balance(account.getBalance())
                .userEmail(account.getUserEmail())
                .version(account.getVersion())
                .createdAt(account.getCreatedAt())
                .build();
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        List<AccountDTO> accounts = accountRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/accounts/count")
    public ResponseEntity<Map<String, Long>> getAccountCount() {
        long count = accountRepository.count();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/users/{email}/accounts")
    public ResponseEntity<List<AccountDTO>> getUserAccounts(@PathVariable String email) {
        List<AccountDTO> accounts = accountRepository.findByUserEmail(email).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        if (!accountRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        try {
            restTemplate.delete(transactionServiceUrl + "/internal/admin/accounts/" + id + "/transactions");
        } catch (Exception e) {
            log.warn("Failed to delete transactions for account {}: {}", id, e.getMessage());
        }

        accountRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{email}/accounts")
    public ResponseEntity<Void> deleteAccountsByUserEmail(@PathVariable String email) {
        List<Account> accounts = accountRepository.findByUserEmail(email);
        for (Account acc : accounts) {
            deleteAccount(acc.getId());
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Apply a specific interest amount to an account balance (called by Admin Service orchestrator).
     */
    @PostMapping("/accounts/{id}/apply-interest")
    public ResponseEntity<Void> applyInterest(@PathVariable Long id,
                                               @RequestParam BigDecimal interestAmount) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        account.setBalance(account.getBalance().add(interestAmount));
        accountRepository.save(account);
        log.info("Applied interest {} to account {}", interestAmount, id);
        return ResponseEntity.ok().build();
    }
}
