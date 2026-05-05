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

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeConfig stripeConfig;

    /**
     * Creates a Stripe Checkout Session for wallet top-up.
     *
     * Returns BOTH the checkout URL and the sessionId.
     * The sessionId must be stored on the PENDING WalletTransaction so the
     * webhook can unambiguously find it — even if a user initiates multiple
     * top-ups simultaneously.
     *
     * Balance is NEVER modified here. Only the webhook handler credits the wallet.
     */
    public CheckoutSessionResult createCheckoutSession(UUID userId, UUID walletId,
                                                       BigDecimal amount, String idempotencyKey) {
        try {
            // Stripe amounts are in cents (integer)
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripeConfig.getSuccessUrl())
                    .setCancelUrl(stripeConfig.getCancelUrl())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(amountInCents)
                                    .setProductData(
                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName("Wallet Top-Up")
                                                    .setDescription("Add funds to your Stripe Wallet")
                                                    .build())
                                    .build())
                            .build())
                    .putMetadata("userId", userId.toString())
                    .putMetadata("walletId", walletId.toString())
                    .putMetadata("idempotencyKey", idempotencyKey)
                    .build();

            Session session = Session.create(params);

            log.info("Created Stripe Checkout Session: sessionId={} userId={}",
                    session.getId(), userId);

            // Return both URL and sessionId — caller must persist the sessionId
            return new CheckoutSessionResult(session.getUrl(), session.getId());

        } catch (StripeException e) {
            log.error("Failed to create Stripe Checkout Session for userId={}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to create payment session: " + e.getMessage());
        }
    }

    /**
     * Verifies the Stripe-Signature header and constructs the Event object.
     * Throws InvalidStripeSignatureException if signature is invalid.
     */
    public Event constructWebhookEvent(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed: {}", e.getMessage());
            throw new InvalidStripeSignatureException("Webhook signature verification failed");
        }
    }

    /**
     * Retrieves a Checkout Session by ID from Stripe.
     */
    public Session retrieveSession(String sessionId) {
        try {
            return Session.retrieve(sessionId);
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe Session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Failed to retrieve payment session");
        }
    }
}
