package com.backend.stripewalletapi.consumer;

import com.backend.stripewalletapi.entity.AuditLog;
import com.backend.stripewalletapi.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

/**
 * AuditConsumer: Production-grade microservice consumer.
 */
@Service
@RequiredArgsConstructor
public class AuditConsumer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditConsumer.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlq"
    )
    @KafkaListener(topics = "wallet-events", groupId = "audit-group")
    public void consume(ConsumerRecord<String, String> record) {
        byte[] traceIdBytes = record.headers().lastHeader("traceId") != null ? 
                record.headers().lastHeader("traceId").value() : null;
        
        if (traceIdBytes != null) {
            MDC.put("traceId", new String(traceIdBytes));
        }

        String message = record.value();
        log.info("AUDIT-SERVICE: Processing event from Kafka. TraceId: {}", MDC.get("traceId"));
        
        try {
            AuditLog logEntry = new AuditLog();
            logEntry.setAction("DISTRIBUTED_AUDIT");
            logEntry.setDescription(message);
            
            auditLogRepository.save(logEntry);
            log.info("AUDIT-SERVICE: Log persisted successfully");
            
        } catch (Exception e) {
            log.error("AUDIT-SERVICE: Error processing event.");
            throw new RuntimeException(e);
        } finally {
            MDC.remove("traceId");
        }
    }

    @KafkaListener(topics = "wallet-events-dlq", groupId = "audit-dlq-group")
    public void handleDlq(String message) {
        log.error("AUDIT-SERVICE-DLQ: Event moved to Dead Letter Queue: {}", message);
    }
}
