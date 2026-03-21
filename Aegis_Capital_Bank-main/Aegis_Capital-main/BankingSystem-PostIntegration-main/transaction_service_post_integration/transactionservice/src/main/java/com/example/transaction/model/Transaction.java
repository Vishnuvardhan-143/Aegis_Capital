
package com.example.transaction.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="transactions")
public class Transaction {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Long id;

 private Long accountId;
 private Long referenceAccountId;
 private String type;
 private Double amount;
 private String status;
 private LocalDateTime createdAt = LocalDateTime.now();

 public Long getId(){return id;}

 public Long getAccountId(){return accountId;}
 public void setAccountId(Long accountId){this.accountId=accountId;}

 public Long getReferenceAccountId(){return referenceAccountId;}
 public void setReferenceAccountId(Long referenceAccountId){this.referenceAccountId=referenceAccountId;}

 public String getType(){return type;}
 public void setType(String type){this.type=type;}

 public Double getAmount(){return amount;}
 public void setAmount(Double amount){this.amount=amount;}

 public String getStatus(){return status;}
 public void setStatus(String status){this.status=status;}
}
