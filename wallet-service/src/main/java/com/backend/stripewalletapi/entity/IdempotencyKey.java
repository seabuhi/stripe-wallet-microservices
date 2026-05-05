package com.backend.stripewalletapi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "idempotency_keys",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_idempotency_user_endpoint_key",
        columnNames = {"key", "user_id", "endpoint"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The idempotency key string sent by the client */
    @Column(nullable = false, length = 255)
    private String key;

    /** Scoped per user — two users can use the same key safely */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Scoped per endpoint — same key can be reused on different endpoints */
    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        expiresAt = createdAt.plusHours(24);
    }
}
