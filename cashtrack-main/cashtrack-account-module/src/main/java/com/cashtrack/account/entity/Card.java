package com.cashtrack.account.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "cards")
@Getter
@Setter
public class Card {

    @Id
    @Column(name = "card_number")
    private String cardNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, BLOCKED

    @PrePersist
    public void generateId() {
        if (cardNumber == null) {
            cardNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }
}
