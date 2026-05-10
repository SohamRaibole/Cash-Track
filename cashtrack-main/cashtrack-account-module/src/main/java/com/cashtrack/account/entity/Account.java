package com.cashtrack.account.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "kyc_details")
    private String kycDetails;

    @Column(name = "balance", nullable = false)
    private double balance;

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, INACTIVE, BLOCKED

    @PrePersist
    public void generateId() {
        if (accountId == null) {
            accountId = UUID.randomUUID().toString();
        }
    }
}
