
package com.example.transaction.dto;

public class DepositRequest{
 private Long accountId;
 private Double amount;

 public Long getAccountId(){return accountId;}
 public void setAccountId(Long accountId){this.accountId=accountId;}

 public Double getAmount(){return amount;}
 public void setAmount(Double amount){this.amount=amount;}

 private String pin;
 public String getPin(){return pin;}
 public void setPin(String pin){this.pin=pin;}
}
