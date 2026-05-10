package com.cashtrack.reconciliation.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "reconciliation_logs")
public class ReconciliationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private LocalDate reconciliationDate;
    private int totalTransactions;
    private int matchedCount;
    private int mismatchedCount;
    private String status; // SUCCESS, PENDING, DISCREPANCY_FOUND

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public LocalDate getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(LocalDate reconciliationDate) { this.reconciliationDate = reconciliationDate; }
    public int getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }
    public int getMatchedCount() { return matchedCount; }
    public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }
    public int getMismatchedCount() { return mismatchedCount; }
    public void setMismatchedCount(int mismatchedCount) { this.mismatchedCount = mismatchedCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}