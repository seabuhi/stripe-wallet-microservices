package com.backend.stripewalletapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock Notification Service to demonstrate decoupled event-driven architecture.
 */
@Slf4j
@Service
public class NotificationService {

    @Async("auditLogExecutor")
    public void sendBalanceNotification(UUID userId, BigDecimal amount, BigDecimal newBalance) {
        log.info("Sending PUSH/EMAIL to user {}: Balance updated by {}. New balance: {}", 
                userId, amount, newBalance);
        
        // Simulating external API call delay
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        
        log.debug("Notification sent successfully to user {}", userId);
    }
}
