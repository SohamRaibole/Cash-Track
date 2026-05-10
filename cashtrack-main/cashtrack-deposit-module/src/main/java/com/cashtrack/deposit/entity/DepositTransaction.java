package com.cashtrack.deposit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "deposits")
@Getter
@Setter
public class DepositTransaction {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "atm_id", nullable = false)
    private String atmId;

    @Column(name = "amount", nullable = false)
    private double amount;

    @Column(name = "status", nullable = false)
    private String status;

    @PrePersist
    public void generateId() {
        if (transactionId == null) {
            transactionId = UUID.randomUUID().toString();
        }
    }
}
