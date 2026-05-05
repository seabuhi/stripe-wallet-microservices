package com.backend.stripewalletapi.service;

import com.backend.stripewalletapi.entity.OutboxEvent;
import com.backend.stripewalletapi.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MessageRelay: Transactional Outbox Relay.
 * Senior pattern: Polling-based relay to ensure at-least-once delivery to Kafka.
 */
@Service
@RequiredArgsConstructor
public class MessageRelay {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MessageRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        
        if (pendingEvents.isEmpty()) return;

        log.info("RELAY: Found {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Exactly-once producer handles deduplication at Kafka side
                kafkaProducerService
                        .sendAsync(event.getAggregateType() + "-events", event.getAggregateId(), event.getPayload())
                        .get(); // Sync wait for ACK to ensure delivery before marking as processed

                event.setStatus("PROCESSED");
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                
                log.info("RELAY: Successfully delivered event {}", event.getId());
            } catch (Exception e) {
                log.error("RELAY: Failed to deliver event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
