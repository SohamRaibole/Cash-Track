package com.cashtrack.fraud.repository;

import com.cashtrack.fraud.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FraudRepository extends JpaRepository<FraudAlert, String> {
    List<FraudAlert> findByAccountId(String accountId);
}