
package com.example.transaction.dto;

public class TransferRequest{
 private Long fromAccountId;
 private Long toAccountId;
 private Double amount;

 public Long getFromAccountId(){return fromAccountId;}
 public void setFromAccountId(Long fromAccountId){this.fromAccountId=fromAccountId;}

 public Long getToAccountId(){return toAccountId;}
 public void setToAccountId(Long toAccountId){this.toAccountId=toAccountId;}

 public Double getAmount(){return amount;}
 public void setAmount(Double amount){this.amount=amount;}

 private String pin;
 public String getPin(){return pin;}
 public void setPin(String pin){this.pin=pin;}
}
