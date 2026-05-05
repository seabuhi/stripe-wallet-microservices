package com.backend.stripewalletapi.consumer;

import com.backend.stripewalletapi.event.PaymentCompletedEvent;
import com.backend.stripewalletapi.event.PaymentFailedEvent;
import com.backend.stripewalletapi.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

/**
 * PaymentConsumer: Consumes payment events from the Payment Service.
 * Senior pattern: Updates balance and transaction status based on distributed events.
 * Includes Retry and DLQ for exactly-once flow stability.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConsumer {

    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlq"
    )
    @KafkaListener(topics = "payment-completed", groupId = "wallet-group")
    public void handlePaymentCompleted(String message) {
        log.info("WALLET-SERVICE: Received payment-completed event");
        try {
            PaymentCompletedEvent event = objectMapper.readValue(message, PaymentCompletedEvent.class);
            walletService.completeTopUp(event.getSessionId(), event.getPaymentIntentId(), 
                    event.getUserId(), event.getAmount());
        } catch (Exception e) {
            log.error("Failed to process payment-completed event. Triggering retry.");
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "wallet-group")
    public void handlePaymentFailed(String message) {
        log.info("WALLET-SERVICE: Received payment-failed event");
        try {
            PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
            walletService.markTransactionFailed(event.getPaymentIntentId(), event.getReason());
        } catch (Exception e) {
            log.error("Failed to process payment-failed event", e);
        }
    }
}
