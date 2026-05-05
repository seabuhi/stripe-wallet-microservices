package com.backend.stripewalletapi.event;

import com.backend.stripewalletapi.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * WalletEventListener: Decoupled side-effect handler.
 * Senior pattern: Listens to domain events. 
 * Distributed Auditing is handled by the 'audit-service' via Kafka.
 * Local side-effects like Notifications are handled here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        log.info("EVENT: Local side-effects for TransactionCompletedEvent: {}", event.getTransactionId());
        
        // Local side-effect: Notification
        notificationService.send(event.getUserId(), "Transaction successful: " + event.getTransactionId());
        
        // Distributed Auditing: audit-service is already listening to 'wallet-events' topic via OutboxRelay
    }

    @EventListener
    public void handleBalanceUpdated(BalanceUpdatedEvent event) {
        log.info("EVENT: User {} balance updated. Change: {}", event.getUserId(), event.getChange());
    }
}
