package com.cashtrack.session.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "atm_sessions")
@Getter
@Setter
public class AtmSession {

    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "atm_id", nullable = false)
    private String atmId;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "status", nullable = false)
    private String status; // INITIATED, AUTHENTICATED, TERMINATED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "token")
    private String token;

    @PrePersist
    public void generateDefaults() {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
            expiresAt = createdAt.plusMinutes(5); // 5 min timeout
        }
    }
}
