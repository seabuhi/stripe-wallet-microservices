package com.backend.stripewalletapi.service;

import com.backend.stripewalletapi.entity.OutboxEvent;
import com.backend.stripewalletapi.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MessageRelay: Polling consumer with reliable acknowledgment handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        
        if (pendingEvents.isEmpty()) return;

        log.info("MessageRelay: Processing {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Synchronous wait for acknowledgment in background polling task
                kafkaProducerService.sendAsync("wallet-events", event.getAggregateId(), event.getPayload()).get();
                
                log.info("KAFKA: Confirmed delivery for event {}", event.getId());
                updateStatus(event, "PROCESSED");
                
            } catch (Exception e) {
                log.error("Relay error for event {}: {}", event.getId(), e.getMessage());
                updateStatus(event, "FAILED");
            }
        }
    }

    private void updateStatus(OutboxEvent event, String status) {
        event.setStatus(status);
        if ("PROCESSED".equals(status)) {
            event.setProcessedAt(LocalDateTime.now());
        }
        outboxEventRepository.save(event);
    }
}
