package com.backend.stripewalletapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final IdempotencyService idempotencyService;

    /**
     * Runs every hour to clean up expired idempotency keys (older than 24h).
     */
    @Scheduled(fixedRate = 3_600_000)
    public void cleanupExpiredIdempotencyKeys() {
        log.info("Running idempotency key cleanup...");
        idempotencyService.cleanupExpiredKeys();
    }
}
