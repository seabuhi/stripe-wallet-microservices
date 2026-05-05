package com.backend.stripewalletapi.service;

import com.backend.stripewalletapi.config.StripeConfig;
import com.backend.stripewalletapi.dto.response.CheckoutSessionResult;
import com.backend.stripewalletapi.exception.InvalidStripeSignatureException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * StripeService: Core Stripe integration logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeConfig stripeConfig;

    /**
     * Creates a Stripe Checkout Session for wallet top-up.
     */
    public CheckoutSessionResult createCheckoutSession(UUID userId, BigDecimal amount, String currency) {
        try {
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripeConfig.getSuccessUrl())
                    .setCancelUrl(stripeConfig.getCancelUrl())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency != null ? currency : "usd")
                                    .setUnitAmount(amountInCents)
                                    .setProductData(
                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName("Wallet Top-Up")
                                                    .setDescription("Add funds to your Stripe Wallet")
                                                    .build())
                                    .build())
                            .build())
                    .putMetadata("userId", userId.toString())
                    .build();

            Session session = Session.create(params);
            log.info("Created Stripe Checkout Session: sessionId={} userId={}", session.getId(), userId);

            return new CheckoutSessionResult(session.getUrl(), session.getId());

        } catch (StripeException e) {
            log.error("Failed to create Stripe Checkout Session for userId={}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to create payment session: " + e.getMessage());
        }
    }

    public Event constructWebhookEvent(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed: {}", e.getMessage());
            throw new InvalidStripeSignatureException("Webhook signature verification failed");
        }
    }

    public Session retrieveSession(String sessionId) {
        try {
            return Session.retrieve(sessionId);
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe Session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Failed to retrieve payment session");
        }
    }
}
