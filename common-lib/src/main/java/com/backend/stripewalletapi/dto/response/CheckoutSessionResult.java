package com.backend.stripewalletapi.dto.response;

/**
 * Holds both the Stripe Checkout URL and the session ID.
 * The session ID is stored on the PENDING transaction so webhook can
 * find the exact transaction later.
 */
public record CheckoutSessionResult(String url, String sessionId) {}
