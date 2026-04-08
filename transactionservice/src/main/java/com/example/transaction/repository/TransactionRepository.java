
package com.example.transaction.repository;

import com.example.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction,Long>{
 List<Transaction> findByAccountId(Long accountId);

 @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId OR t.referenceAccountId = :accountId ORDER BY t.createdAt DESC")
 List<Transaction> findByAccountIdOrReferenceAccountId(@Param("accountId") Long accountId);
}
