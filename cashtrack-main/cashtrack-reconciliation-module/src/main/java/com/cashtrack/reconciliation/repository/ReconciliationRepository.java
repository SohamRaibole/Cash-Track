package com.cashtrack.reconciliation.repository;

import com.cashtrack.reconciliation.entity.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDate;

public interface ReconciliationRepository extends JpaRepository<ReconciliationLog, String> {
    Optional<ReconciliationLog> findByReconciliationDate(LocalDate date);
}