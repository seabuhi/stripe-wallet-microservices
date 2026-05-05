package com.backend.stripewalletapi.service;

import com.backend.stripewalletapi.entity.IdempotencyKey;
import com.backend.stripewalletapi.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository repository;

    @Transactional(readOnly = true)
    public Optional<String> getResponse(String key, String endpoint) {
        return repository.findByKeyAndEndpoint(key, endpoint)
                .map(IdempotencyKey::getResponse);
    }

    @Transactional
    public void saveResponse(String key, String endpoint, String response) {
        IdempotencyKey idempotencyKey = new IdempotencyKey();
        idempotencyKey.setKey(key);
        idempotencyKey.setEndpoint(endpoint);
        idempotencyKey.setResponse(response);
        idempotencyKey.setCreatedAt(LocalDateTime.now());
        
        repository.save(idempotencyKey);
        log.debug("IDEMPOTENCY: Saved response for key: {}", key);
    }

    @Transactional
    public void cleanupExpiredKeys() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        repository.deleteByCreatedAtBefore(threshold);
        log.info("IDEMPOTENCY: Cleaned up keys older than {}", threshold);
    }
}
