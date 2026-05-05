package com.backend.stripewalletapi.repository;

import com.backend.stripewalletapi.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByKeyAndEndpoint(String key, String endpoint);

    void deleteByCreatedAtBefore(LocalDateTime threshold);
}
