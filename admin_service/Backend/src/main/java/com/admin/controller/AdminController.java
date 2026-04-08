package com.admin.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final RestTemplate restTemplate;

    @Value("${auth-service.url:http://auth-backend:5052}")
    private String authServiceUrl;

    @Value("${account-service.url:http://account-backend:5050}")
    private String accountServiceUrl;

    @Value("${transaction-service.url:http://transaction-backend:5005}")
    private String transactionServiceUrl;

    // ─── USER ENDPOINTS ────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                authServiceUrl + "/internal/admin/users",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        return ResponseEntity.ok(response.getBody());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        restTemplate.delete(authServiceUrl + "/internal/admin/users/" + id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer-admin")
    public ResponseEntity<Void> transferAdmin(@RequestParam String currentAdminEmail, @RequestParam String newAdminEmail) {
        restTemplate.postForEntity(
                authServiceUrl + "/internal/admin/transfer-admin?currentAdminEmail=" + currentAdminEmail + "&newAdminEmail=" + newAdminEmail,
                null,
                Void.class
        );
        return ResponseEntity.ok().build();
    }

    // ─── ACCOUNT ENDPOINTS ────────────────────────────────────────────────────

    @GetMapping("/users/{email}/accounts")
    public ResponseEntity<List<Map<String, Object>>> getUserAccounts(@PathVariable String email) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                accountServiceUrl + "/internal/admin/users/" + email + "/accounts",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        return ResponseEntity.ok(response.getBody());
    }

    /**
     * Returns all accounts across all users — used by Accounts Overview nav.
     */
    @GetMapping("/accounts/all")
    public ResponseEntity<List<Map<String, Object>>> getAllAccounts() {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                accountServiceUrl + "/internal/admin/accounts",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        return ResponseEntity.ok(response.getBody());
    }

    /**
     * Returns count of all accounts + total system liquidity.
     */
    @GetMapping("/accounts/stats")
    public ResponseEntity<Map<String, Object>> getAccountStats() {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    accountServiceUrl + "/internal/admin/accounts",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> accounts = response.getBody();
            if (accounts == null) accounts = List.of();

            long count = accounts.size();
            double totalLiquidity = accounts.stream()
                    .mapToDouble(acc -> {
                        Object bal = acc.get("balance");
                        if (bal instanceof Number) return ((Number) bal).doubleValue();
                        return 0.0;
                    })
                    .sum();

            Map<String, Object> stats = new HashMap<>();
            stats.put("count", count);
            stats.put("totalLiquidity", totalLiquidity);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to fetch account stats: {}", e.getMessage());
            Map<String, Object> stats = new HashMap<>();
            stats.put("count", 0);
            stats.put("totalLiquidity", 0.0);
            return ResponseEntity.ok(stats);
        }
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        restTemplate.delete(accountServiceUrl + "/internal/admin/accounts/" + id);
        return ResponseEntity.ok().build();
    }

    // ─── TRANSACTION ENDPOINTS ────────────────────────────────────────────────

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<List<Map<String, Object>>> getAccountTransactions(@PathVariable Long accountId) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                transactionServiceUrl + "/internal/admin/accounts/" + accountId + "/transactions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        return ResponseEntity.ok(response.getBody());
    }

    // ─── INTEREST ENDPOINT ────────────────────────────────────────────────────

    /**
     * Applies 0.5% monthly interest to accounts.
     * If a list of userEmails is provided in the body, only those users' accounts are processed.
     * If the list is empty or null, ALL accounts are processed.
     */
    @PostMapping("/apply-interest")
    public ResponseEntity<Map<String, Object>> applyMonthlyInterest(
            @RequestBody(required = false) Map<String, Object> requestBody) {

        final double MONTHLY_RATE = 0.005; // 0.5%

        // Extract optional email filter
        List<String> targetEmails = null;
        if (requestBody != null && requestBody.containsKey("emails")) {
            Object emailsObj = requestBody.get("emails");
            if (emailsObj instanceof List) {
                targetEmails = ((List<?>) emailsObj).stream()
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.toList());
            }
        }

        // 1. Fetch all accounts
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                accountServiceUrl + "/internal/admin/accounts",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        List<Map<String, Object>> accounts = response.getBody();
        if (accounts == null || accounts.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("accountsProcessed", 0);
            result.put("totalInterestApplied", 0.0);
            return ResponseEntity.ok(result);
        }

        // 2. Filter by target emails if specified
        if (targetEmails != null && !targetEmails.isEmpty()) {
            final List<String> finalTargetEmails = targetEmails;
            accounts = accounts.stream()
                    .filter(acc -> {
                        Object email = acc.get("userEmail");
                        return email != null && finalTargetEmails.contains(email.toString());
                    })
                    .collect(java.util.stream.Collectors.toList());
        }

        int processed = 0;
        double totalInterest = 0.0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (Map<String, Object> acc : accounts) {
            try {
                Long accountId = Long.valueOf(acc.get("id").toString());
                Object balObj = acc.get("balance");
                double balance = balObj instanceof Number ? ((Number) balObj).doubleValue() : 0.0;

                // Determine months elapsed (Default to 1 if NULL for existing accounts)
                long monthsElapsed = 1;
                Object createdAtObj = acc.get("createdAt");
                if (createdAtObj != null) {
                    if (createdAtObj instanceof List) {
                        List<?> dateArr = (List<?>) createdAtObj;
                        if (dateArr.size() >= 3) {
                            int year = ((Number) dateArr.get(0)).intValue();
                            int month = ((Number) dateArr.get(1)).intValue();
                            int day = ((Number) dateArr.get(2)).intValue();
                            LocalDateTime createdAt = LocalDateTime.of(year, month, day, 0, 0);
                            monthsElapsed = Math.max(1, ChronoUnit.MONTHS.between(createdAt, LocalDateTime.now()));
                        }
                    } else if (createdAtObj instanceof String) {
                        LocalDateTime createdAt = LocalDateTime.parse(createdAtObj.toString().replace(" ", "T"));
                        monthsElapsed = Math.max(1, ChronoUnit.MONTHS.between(createdAt, LocalDateTime.now()));
                    }
                }

                BigDecimal interestAmount = BigDecimal.valueOf(balance * MONTHLY_RATE * monthsElapsed)
                        .setScale(2, RoundingMode.HALF_UP);

                if (interestAmount.compareTo(BigDecimal.ZERO) > 0) {
                    // 3. Apply interest to account balance
                    restTemplate.postForEntity(
                            accountServiceUrl + "/internal/admin/accounts/" + accountId
                                    + "/apply-interest?interestAmount=" + interestAmount,
                            null, Void.class
                    );

                    // 4. Record interest transaction
                    restTemplate.postForEntity(
                            transactionServiceUrl + "/internal/admin/accounts/" + accountId
                                    + "/record-interest?amount=" + interestAmount,
                            null, Void.class
                    );

                    totalInterest += interestAmount.doubleValue();
                    processed++;
                }

            } catch (Exception e) {
                log.error("Failed to apply interest to account {}: {}", acc.get("id"), e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("accountsProcessed", processed);
        result.put("totalInterestApplied", BigDecimal.valueOf(totalInterest).setScale(2, RoundingMode.HALF_UP));
        return ResponseEntity.ok(result);
    }
}
