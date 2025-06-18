package com.auth0.example;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByCategoryId(Long categoryId);
    List<Transaction> findByCategoryIdAndUserMail(Long categoryId, String userMail);
    List<Transaction> findByUserMail(String userMail);
} 