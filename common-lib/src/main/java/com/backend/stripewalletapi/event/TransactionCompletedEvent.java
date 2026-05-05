package com.backend.stripewalletapi.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * TransactionCompletedEvent: Decoupled domain event.
 * Senior pattern: Uses IDs/Strings instead of direct Entity references 
 * to maintain clean module boundaries.
 */
@Getter
public class TransactionCompletedEvent extends ApplicationEvent {
    private final UUID userId;
    private final UUID transactionId;
    private final String eventType;
    private final String ipAddress;

    public TransactionCompletedEvent(Object source, UUID userId, UUID transactionId, String eventType, String ipAddress) {
        super(source);
        this.userId = userId;
        this.transactionId = transactionId;
        this.eventType = eventType;
        this.ipAddress = ipAddress;
    }
}
