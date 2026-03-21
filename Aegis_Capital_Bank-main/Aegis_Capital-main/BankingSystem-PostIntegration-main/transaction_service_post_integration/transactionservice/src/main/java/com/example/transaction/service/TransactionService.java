package com.example.transaction.service;

import com.example.transaction.dto.*;
import com.example.transaction.model.Transaction;
import com.example.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;

@Service
public class TransactionService{

 private final TransactionRepository repository;
 private final RestTemplate restTemplate;

 private static final String ACCOUNT_SERVICE_URL = "http://localhost:5050/internal/accounts";

 public TransactionService(TransactionRepository repository, RestTemplate restTemplate){
  this.repository=repository;
  this.restTemplate=restTemplate;
 }

 private boolean verifyPin(Long accountId, String pin) {
  try {
   ResponseEntity<Boolean> response = restTemplate.postForEntity(
    ACCOUNT_SERVICE_URL + "/" + accountId + "/verify-pin?pin=" + pin,
    null,
    Boolean.class
   );
   return response.getStatusCode() == HttpStatus.OK && Boolean.TRUE.equals(response.getBody());
  } catch (Exception e) {
   return false;
  }
 }

 @Transactional
 public Transaction deposit(DepositRequest req){
  Transaction tx=new Transaction();
  tx.setAccountId(req.getAccountId());
  tx.setAmount(req.getAmount());
  tx.setType("DEPOSIT");

  if (!verifyPin(req.getAccountId(), req.getPin())) {
   tx.setStatus("FAILED: INVALID PIN");
   return repository.save(tx);
  }

  tx.setStatus("SUCCESS");

  // Update balance in Account Service
  try {
   restTemplate.put(
     ACCOUNT_SERVICE_URL + "/" + req.getAccountId() + "/balance?amount=" + req.getAmount() + "&type=DEPOSIT",
     null
   );
  } catch (Exception e) {
   tx.setStatus("FAILED");
  }

  return repository.save(tx);
 }

 @Transactional
 public Transaction withdraw(WithdrawRequest req){
  Transaction tx=new Transaction();
  tx.setAccountId(req.getAccountId());
  tx.setAmount(req.getAmount());
  tx.setType("WITHDRAW");

  if (!verifyPin(req.getAccountId(), req.getPin())) {
   tx.setStatus("FAILED: INVALID PIN");
   return repository.save(tx);
  }

  tx.setStatus("SUCCESS");

  // Update balance in Account Service
  try {
   restTemplate.put(
     ACCOUNT_SERVICE_URL + "/" + req.getAccountId() + "/balance?amount=" + req.getAmount() + "&type=WITHDRAW",
     null
   );
  } catch (Exception e) {
   tx.setStatus("FAILED");
  }

  return repository.save(tx);
 }

 @Transactional
 public Transaction transfer(TransferRequest req){
  Transaction tx=new Transaction();
  tx.setAccountId(req.getFromAccountId());
  tx.setReferenceAccountId(req.getToAccountId());
  tx.setAmount(req.getAmount());
  tx.setType("TRANSFER");

  if (!verifyPin(req.getFromAccountId(), req.getPin())) {
   tx.setStatus("FAILED: INVALID PIN");
   return repository.save(tx);
  }

  tx.setStatus("SUCCESS");

  // Withdraw from source, deposit to destination
  try {
   restTemplate.put(
     ACCOUNT_SERVICE_URL + "/" + req.getFromAccountId() + "/balance?amount=" + req.getAmount() + "&type=WITHDRAW",
     null
   );
   restTemplate.put(
     ACCOUNT_SERVICE_URL + "/" + req.getToAccountId() + "/balance?amount=" + req.getAmount() + "&type=DEPOSIT",
     null
   );
  } catch (Exception e) {
   tx.setStatus("FAILED");
  }

  return repository.save(tx);
 }

 public List<Transaction> getHistory(Long accountId){
  return repository.findByAccountId(accountId);
 }
}

