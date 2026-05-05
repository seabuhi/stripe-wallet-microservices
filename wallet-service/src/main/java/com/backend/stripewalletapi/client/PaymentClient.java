package com.backend.stripewalletapi.client;

import com.backend.stripewalletapi.dto.response.CheckoutSessionResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PaymentClient: Declarative REST client for service-to-service communication.
 * Senior pattern: Wallet calls Payment service to initiate Stripe sessions.
 */
@FeignClient(name = "payment-service", url = "${payment.service.url:http://payment-service:8083}")
public interface PaymentClient {

    @PostMapping("/api/v1/stripe/checkout-session")
    CheckoutSessionResult createCheckoutSession(
            @RequestParam("userId") UUID userId,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("currency") String currency
    );
}
