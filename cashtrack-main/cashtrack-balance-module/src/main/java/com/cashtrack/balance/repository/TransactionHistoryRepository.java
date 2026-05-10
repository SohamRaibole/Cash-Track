package com.cashtrack.balance.repository;

import com.cashtrack.balance.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionHistoryRepository extends JpaRepository<TransactionRecord, String> {
    List<TransactionRecord> findByAccountIdOrderByTimestampDesc(String accountId);
}