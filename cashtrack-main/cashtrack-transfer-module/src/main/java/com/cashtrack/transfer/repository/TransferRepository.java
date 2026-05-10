package com.cashtrack.transfer.repository;

import com.cashtrack.transfer.entity.TransferTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferRepository extends JpaRepository<TransferTransaction, String> {
}
