package com.backend.stripewalletapi.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event published specifically when a wallet balance changes.
 * Useful for real-time analytics, push notifications, or cache invalidation.
 */
@Getter
public class BalanceUpdatedEvent extends ApplicationEvent {

    private final UUID userId;
    private final BigDecimal newBalance;
    private final BigDecimal changeAmount;

    public BalanceUpdatedEvent(Object source, UUID userId, BigDecimal newBalance, BigDecimal changeAmount) {
        super(source);
        this.userId = userId;
        this.newBalance = newBalance;
        this.changeAmount = changeAmount;
    }
}
