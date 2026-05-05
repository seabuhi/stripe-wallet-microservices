package com.backend.stripewalletapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * FallbackController: Handles service failures gracefully.
 * Senior pattern: Provides degraded functionality instead of 5xx errors.
 */
@RestController
public class FallbackController {

    @GetMapping("/fallback/wallet")
    public Mono<Map<String, String>> walletFallback() {
        return Mono.just(Map.of(
            "status", "DEGRADED",
            "message", "Wallet service is temporarily unavailable. Please try again later.",
            "code", "WALLET_SERVICE_DOWN"
        ));
    }
}
