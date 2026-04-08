package com.example.transaction.service;

import com.example.transaction.dto.*;
import com.example.transaction.model.Transaction;
import com.example.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService{

 private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

 private final TransactionRepository repository;
 private final RestTemplate restTemplate;
 private final String accountServiceBaseUrl;

 public TransactionService(TransactionRepository repository, RestTemplate restTemplate,
         @Value("${account-service.url:http://localhost:5050}") String accountServiceBaseUrl){
  this.repository=repository;
  this.restTemplate=restTemplate;
  this.accountServiceBaseUrl=accountServiceBaseUrl;
 }

 private boolean verifyPin(Long accountId, String pin) {
  try {
   ResponseEntity<Boolean> response = restTemplate.postForEntity(
    accountServiceBaseUrl + "/internal/accounts" + "/" + accountId + "/verify-pin?pin=" + pin,
    null,
    Boolean.class
   );
   return response.getStatusCode() == HttpStatus.OK && Boolean.TRUE.equals(response.getBody());
  } catch (Exception e) {
   log.error("PIN verification failed for account {}: {}", accountId, e.getMessage());
   return false;
  }
 }

 /**
  * Calls the Account Service to update balance.
  * @throws RuntimeException if the call fails
  */
 private void updateAccountBalance(Long accountId, BigDecimal amount, String type) {
  restTemplate.put(
    accountServiceBaseUrl + "/internal/accounts/" + accountId + "/balance?amount=" + amount + "&type=" + type,
    null
  );
 }

 @Transactional(noRollbackFor = Exception.class)
 public Transaction deposit(DepositRequest req){
  Transaction tx=new Transaction();
  tx.setAccountId(req.getAccountId());
  tx.setAmount(req.getAmount());
  tx.setType("DEPOSIT");
  tx.setStatus("PENDING");
  tx = repository.save(tx);
  log.info("DEPOSIT tx#{} created for account {}", tx.getId(), req.getAccountId());

  if (!verifyPin(req.getAccountId(), req.getPin())) {
   tx.setStatus("FAILED: INVALID PIN");
   log.warn("DEPOSIT tx#{} FAILED: INVALID PIN", tx.getId());
   return repository.save(tx);
  }

  try {
   updateAccountBalance(req.getAccountId(), req.getAmount(), "DEPOSIT");
   tx.setStatus("SUCCESS");
   log.info("DEPOSIT tx#{} SUCCESS", tx.getId());
  } catch (Exception e) {
   tx.setStatus("FAILED: " + extractErrorMessage(e));
   log.error("DEPOSIT tx#{} FAILED: {}", tx.getId(), e.getMessage());
  } finally {
   repository.save(tx);
  }

  return tx;
 }

 @Transactional(noRollbackFor = Exception.class)
 public Transaction withdraw(WithdrawRequest req){
  Transaction tx=new Transaction();
  tx.setAccountId(req.getAccountId());
  tx.setAmount(req.getAmount());
  tx.setType("WITHDRAW");
  tx.setStatus("PENDING");
  tx = repository.save(tx);
  log.info("WITHDRAW tx#{} created for account {}", tx.getId(), req.getAccountId());

  if (!verifyPin(req.getAccountId(), req.getPin())) {
   tx.setStatus("FAILED: INVALID PIN");
   log.warn("WITHDRAW tx#{} FAILED: INVALID PIN", tx.getId());
   return repository.save(tx);
  }

  try {
   updateAccountBalance(req.getAccountId(), req.getAmount(), "WITHDRAW");
   tx.setStatus("SUCCESS");
   log.info("WITHDRAW tx#{} SUCCESS", tx.getId());
  } catch (Exception e) {
   tx.setStatus("FAILED: " + extractErrorMessage(e));
   log.error("WITHDRAW tx#{} FAILED: {}", tx.getId(), e.getMessage());
  } finally {
   repository.save(tx);
  }

  return tx;
 }

 /**
  * Public transfer method — delegates to the private saga method.
  * This method only handles PIN verification. All money movement
  * and rollback logic is inside executeTransferSaga() which uses
  * a try-finally block that CANNOT be bypassed.
  */
 @Transactional(noRollbackFor = Exception.class)
 public Transaction transfer(TransferRequest req){
  Transaction tx=new Transaction();
  tx.setAccountId(req.getFromAccountId());
  tx.setReferenceAccountId(req.getToAccountId());
  tx.setAmount(req.getAmount());
  tx.setType("TRANSFER");
  tx.setStatus("PENDING");
  tx = repository.save(tx);
  log.info("TRANSFER tx#{} created: {} -> {} amount={}", tx.getId(), req.getFromAccountId(), req.getToAccountId(), req.getAmount());

  if (!verifyPin(req.getFromAccountId(), req.getPin())) {
   tx.setStatus("FAILED: INVALID PIN");
   log.warn("TRANSFER tx#{} FAILED: INVALID PIN", tx.getId());
   return repository.save(tx);
  }

  // Execute the saga (withdraw → deposit → rollback-if-needed)
  executeTransferSaga(tx, req);

  return tx;
 }

 /**
  * Executes the two-step saga for a transfer:
  *   Step 1: Withdraw from source account
  *   Step 2: Deposit to destination account
  *
  * If step 1 succeeds but step 2 fails (for ANY reason — exception, crash,
  * injected code, null transaction, etc.), the finally block ALWAYS runs and
  * will automatically REFUND the source account.
  *
  * Java guarantees that finally blocks execute regardless of:
  *   - Normal return
  *   - return statements inside try/catch
  *   - Any thrown Exception or Error (except System.exit)
  */
 private void executeTransferSaga(Transaction tx, TransferRequest req) {
  boolean withdrawSuccess = false;
  boolean depositSuccess = false;

  try {
   // ── Step 1: Withdraw from source account ──
   log.info("TRANSFER tx#{} Step 1: Withdrawing {} from account {}", tx.getId(), req.getAmount(), req.getFromAccountId());
   updateAccountBalance(req.getFromAccountId(), req.getAmount(), "WITHDRAW");
   withdrawSuccess = true;
   log.info("TRANSFER tx#{} Step 1 SUCCESS: Withdraw complete", tx.getId());

   // ── Step 2: Deposit to destination account ──
   log.info("TRANSFER tx#{} Step 2: Depositing {} to account {}", tx.getId(), req.getAmount(), req.getToAccountId());
   updateAccountBalance(req.getToAccountId(), req.getAmount(), "DEPOSIT");
   depositSuccess = true;
   tx.setStatus("SUCCESS");
   log.info("TRANSFER tx#{} Step 2 SUCCESS: Deposit complete. Transfer fully successful.", tx.getId());

  } catch (Exception e) {
   log.error("TRANSFER tx#{} FAILED during saga: {}", tx.getId(), e.getMessage());
   tx.setStatus("FAILED: " + extractErrorMessage(e));

  } finally {
   // ── ROLLBACK CHECK: This block ALWAYS executes ──
   // If we successfully withdrew money but did NOT deposit it,
   // we MUST refund the source account.
   if (withdrawSuccess && !depositSuccess) {
    log.warn("TRANSFER tx#{} ROLLBACK TRIGGERED: Withdraw succeeded but deposit did not. Refunding source account {}",
             tx.getId(), req.getFromAccountId());
    try {
     updateAccountBalance(req.getFromAccountId(), req.getAmount(), "DEPOSIT");
     tx.setStatus("FAILED: Transfer aborted after debit. Amount of " + req.getAmount() + " has been refunded to source account " + req.getFromAccountId() + ".");
     log.info("TRANSFER tx#{} ROLLBACK SUCCESS: {} refunded to account {}", tx.getId(), req.getAmount(), req.getFromAccountId());
    } catch (Exception rollbackEx) {
     tx.setStatus("FAILED: CRITICAL - Withdraw succeeded but deposit and rollback both failed. Manual review needed. Amount: " + req.getAmount());
     log.error("TRANSFER tx#{} ROLLBACK FAILED: CRITICAL! Amount {} stuck in limbo. Error: {}", tx.getId(), req.getAmount(), rollbackEx.getMessage());
    }
   }
   // Always save the final transaction status to the database
   repository.save(tx);
   log.info("TRANSFER tx#{} final status saved to DB: {}", tx.getId(), tx.getStatus());
  }
 }

 public List<Transaction> getHistory(Long accountId){
  return repository.findByAccountIdOrReferenceAccountId(accountId);
 }



 /**
  * Extracts a user-friendly error message from exceptions thrown by RestTemplate
  */
 private String extractErrorMessage(Exception e) {
  String msg = e.getMessage();
  if (msg != null && msg.contains("Insufficient balance")) {
   return "Insufficient balance";
  }
  if (msg != null && msg.length() > 100) {
   return msg.substring(0, 100);
  }
  return msg != null ? msg : "Unknown error";
 }
}
