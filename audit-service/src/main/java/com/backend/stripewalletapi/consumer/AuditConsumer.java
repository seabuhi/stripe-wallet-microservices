package com.backend.stripewalletapi.consumer;

import com.backend.stripewalletapi.entity.AuditLog;
import com.backend.stripewalletapi.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AuditConsumer: Production-grade microservice consumer.
 * Senior pattern: Includes Trace Propagation, Retryable Topics, and DLQ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditConsumer {

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
        // Step 1: Extract TraceId from Kafka Headers for distributed correlation
        byte[] traceIdBytes = record.headers().lastHeader("traceId") != null ? 
                record.headers().lastHeader("traceId").value() : null;
        
        if (traceIdBytes != null) {
            MDC.put("traceId", new String(traceIdBytes));
        }

        String message = record.value();
        log.info("AUDIT-SERVICE: Processing event from Kafka. TraceId: {}", MDC.get("traceId"));
        
        try {
            if (message.contains("FAIL_ME")) throw new RuntimeException("Simulated failure");

            AuditLog logEntry = AuditLog.builder()
                    .action("DISTRIBUTED_AUDIT")
                    .description(message)
                    .build();
            
            auditLogRepository.save(logEntry);
            log.info("AUDIT-SERVICE: Log persisted successfully");
            
        } catch (Exception e) {
            log.error("AUDIT-SERVICE: Error processing event. Triggering Kafka retry.");
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
