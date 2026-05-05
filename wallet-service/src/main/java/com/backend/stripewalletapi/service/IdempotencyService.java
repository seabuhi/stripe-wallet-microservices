package com.backend.stripewalletapi.service;

import com.backend.stripewalletapi.entity.IdempotencyKey;
import com.backend.stripewalletapi.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    /**
     * Looks up a cached response scoped to (key + userId + endpoint).
     * Two users with the same key string never interfere with each other.
     */
    @Transactional(readOnly = true)
    public Optional<String> findCachedResponse(String key, UUID userId, String endpoint) {
        return idempotencyKeyRepository
                .findByKeyAndUserIdAndEndpoint(key, userId, endpoint)
                .filter(ik -> ik.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(IdempotencyKey::getResponseBody);
    }

    /**
     * Saves the response body for future duplicate detection.
     * Scoped to (key + userId + endpoint) — safe for multi-tenant use.
     */
    @Transactional
    public void saveResponse(String key, UUID userId, String endpoint, String responseBody) {
        if (idempotencyKeyRepository.existsByKeyAndUserIdAndEndpoint(key, userId, endpoint)) {
            // Update response body (e.g., if processing completed after initial save)
            idempotencyKeyRepository
                    .findByKeyAndUserIdAndEndpoint(key, userId, endpoint)
                    .ifPresent(ik -> {
                        ik.setResponseBody(responseBody);
                        idempotencyKeyRepository.save(ik);
                    });
            return;
        }

        IdempotencyKey ik = IdempotencyKey.builder()
                .key(key)
                .userId(userId)
                .endpoint(endpoint)
                .responseBody(responseBody)
                .build();
        idempotencyKeyRepository.save(ik);
        log.debug("Saved idempotency key={} endpoint={} userId={}", key, endpoint, userId);
    }

    @Transactional
    public void cleanupExpiredKeys() {
        idempotencyKeyRepository.deleteExpiredKeys(LocalDateTime.now());
        log.info("Cleaned up expired idempotency keys");
    }
}
