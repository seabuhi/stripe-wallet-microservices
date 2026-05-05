package com.backend.stripewalletapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String apiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    public String getApiKey() { return apiKey; }
    public String getWebhookSecret() { return webhookSecret; }
    public String getSuccessUrl() { return successUrl; }
    public String getCancelUrl() { return cancelUrl; }
}
