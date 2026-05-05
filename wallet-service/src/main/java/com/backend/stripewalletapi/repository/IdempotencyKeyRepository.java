package com.backend.stripewalletapi.repository;

import com.backend.stripewalletapi.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    /**
     * Scoped lookup: key + userId + endpoint
     * Guarantees two different users with the same key string never collide.
     */
    Optional<IdempotencyKey> findByKeyAndUserIdAndEndpoint(String key, UUID userId, String endpoint);

    boolean existsByKeyAndUserIdAndEndpoint(String key, UUID userId, String endpoint);

    @Modifying
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt < :now")
    void deleteExpiredKeys(LocalDateTime now);
}
