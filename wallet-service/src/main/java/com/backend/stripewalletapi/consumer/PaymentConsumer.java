package com.backend.stripewalletapi.consumer;

import com.backend.stripewalletapi.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentConsumer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentConsumer.class);

    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlq"
    )
    @KafkaListener(topics = "payment-events", groupId = "wallet-payment-group")
    public void consume(ConsumerRecord<String, String> record) {
        String payload = record.value();
        log.info("KAFKA: Received payment event: {}", payload);

        try {
            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            String type = (String) event.get("type");

            if ("PAYMENT_SUCCESS".equals(type)) {
                String sessionId = (String) event.get("sessionId");
                String paymentIntentId = (String) event.get("paymentIntentId");
                UUID userId = UUID.fromString((String) event.get("userId"));
                BigDecimal amount = new BigDecimal(event.get("amount").toString());

                walletService.completeTopUp(sessionId, paymentIntentId, userId, amount);
                log.info("KAFKA: Successfully processed payment for user {}", userId);
            }
        } catch (Exception e) {
            log.error("KAFKA: Error processing payment event: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "payment-events-dlq", groupId = "wallet-payment-dlq-group")
    public void handleDlq(String message) {
        log.error("KAFKA-DLQ: Payment event failed all retries: {}", message);
    }
}
