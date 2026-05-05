package com.backend.stripewalletapi.controller;

import com.backend.stripewalletapi.dto.response.CheckoutSessionResult;
import com.backend.stripewalletapi.service.StripeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * StripeInternalController: Internal endpoint for service-to-service communication.
 * Senior pattern: Allows other microservices to request Stripe operations.
 */
@RestController
@RequestMapping("/api/v1/stripe")
@RequiredArgsConstructor
public class StripeInternalController {

    private final StripeService stripeService;

    @PostMapping("/checkout-session")
    public CheckoutSessionResult createCheckoutSession(
            @RequestParam UUID userId,
            @RequestParam BigDecimal amount,
            @RequestParam String currency
    ) {
        return stripeService.createCheckoutSession(userId, amount, currency);
    }
}
