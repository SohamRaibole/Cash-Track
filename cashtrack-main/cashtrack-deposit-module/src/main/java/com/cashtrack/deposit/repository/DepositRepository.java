package com.cashtrack.deposit.repository;

import com.cashtrack.deposit.entity.DepositTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepositRepository extends JpaRepository<DepositTransaction, String> {
}
