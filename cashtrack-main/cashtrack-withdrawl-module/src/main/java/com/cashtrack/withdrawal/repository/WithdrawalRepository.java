package com.cashtrack.withdrawal.repository;

import com.cashtrack.withdrawal.entity.WithdrawalTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalRepository extends JpaRepository<WithdrawalTransaction, String> {
}
