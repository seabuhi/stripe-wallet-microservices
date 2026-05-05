package com.backend.stripewalletapi.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

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

    public UUID getUserId() { return userId; }
    public BigDecimal getNewBalance() { return newBalance; }
    public BigDecimal getChangeAmount() { return changeAmount; }
}
