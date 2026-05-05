package com.backend.stripewalletapi.event;

import com.backend.stripewalletapi.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * WalletEventListener: Decoupled side-effect handler.
 */
@Component
@RequiredArgsConstructor
public class WalletEventListener {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WalletEventListener.class);

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        log.info("EVENT: Local side-effects for TransactionCompletedEvent: {}", event.getTransactionId());
        notificationService.sendBalanceNotification(event.getUserId(), java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
    }

    @EventListener
    public void handleBalanceUpdated(BalanceUpdatedEvent event) {
        log.info("EVENT: User {} balance updated. Change: {}", event.getUserId(), event.getChangeAmount());
    }
}
