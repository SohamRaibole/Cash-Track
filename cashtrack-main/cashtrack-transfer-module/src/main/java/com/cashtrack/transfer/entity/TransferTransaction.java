package com.cashtrack.transfer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "transfers")
@Getter
@Setter
public class TransferTransaction {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "source_account_id", nullable = false)
    private String sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private String targetAccountId;

    @Column(name = "amount", nullable = false)
    private double amount;

    @Column(name = "status", nullable = false)
    private String status; // INITIATED, VALIDATED, EXECUTED, COMPLETED, ROLLBACKED

    @PrePersist
    public void generateId() {
        if (transactionId == null) {
            transactionId = UUID.randomUUID().toString();
        }
    }
}
