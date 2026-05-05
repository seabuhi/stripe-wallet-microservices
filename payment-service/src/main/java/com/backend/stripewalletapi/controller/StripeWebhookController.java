package com.backend.stripewalletapi.controller;

import com.backend.stripewalletapi.dto.response.ApiResponse;
import com.backend.stripewalletapi.event.PaymentCompletedEvent;
import com.backend.stripewalletapi.event.PaymentFailedEvent;
import com.backend.stripewalletapi.service.StripeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * StripeWebhookController: Independent payment service controller.
 * Senior pattern: Communicates with Wallet service via Kafka (Event-Driven).
 * No direct coupling to WalletService.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stripe")
@RequiredArgsConstructor
@Tag(name = "Stripe Webhook")
public class StripeWebhookController {

    private final StripeService stripeService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    @Operation(summary = "Stripe webhook receiver")
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event = stripeService.constructWebhookEvent(payload, sigHeader);
        log.info("Received Stripe event: type={} id={}", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> publishPaymentCompleted(event);
                case "payment_intent.payment_failed" -> publishPaymentFailed(event);
                default -> log.debug("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Failed to process Stripe event: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Event processing failed"));
        }

        return ResponseEntity.ok(ApiResponse.success("Event received", event.getType()));
    }

    private void publishPaymentCompleted(Event event) throws JsonProcessingException {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject()
                .orElseThrow(() -> new RuntimeException("Failed to deserialize"));

        if (stripeObject instanceof Session session) {
            PaymentCompletedEvent payload = PaymentCompletedEvent.builder()
                    .sessionId(session.getId())
                    .paymentIntentId(session.getPaymentIntent())
                    .userId(UUID.fromString(session.getMetadata().get("userId")))
                    .amount(BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100)))
                    .build();

            kafkaTemplate.send("payment-completed", payload.getUserId().toString(), 
                    objectMapper.writeValueAsString(payload));
            log.info("Published PaymentCompletedEvent for userId={}", payload.getUserId());
        }
    }

    private void publishPaymentFailed(Event event) throws JsonProcessingException {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        deserializer.getObject().ifPresent(obj -> {
            if (obj instanceof PaymentIntent pi) {
                String reason = pi.getLastPaymentError() != null ? pi.getLastPaymentError().getMessage() : "unknown";
                PaymentFailedEvent payload = new PaymentFailedEvent(pi.getId(), reason);
                
                try {
                    kafkaTemplate.send("payment-failed", pi.getId(), objectMapper.writeValueAsString(payload));
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize payment failed event", e);
                }
            }
        });
    }
}
