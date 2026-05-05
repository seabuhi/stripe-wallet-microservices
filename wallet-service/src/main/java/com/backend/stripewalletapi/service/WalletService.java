package com.backend.stripewalletapi.service;

import com.backend.stripewalletapi.dto.request.RefundRequest;
import com.backend.stripewalletapi.dto.request.TopUpRequest;
import com.backend.stripewalletapi.dto.request.TransferRequest;
import com.backend.stripewalletapi.dto.response.BalanceResponse;
import com.backend.stripewalletapi.dto.response.CheckoutSessionResult;
import com.backend.stripewalletapi.dto.response.TopUpResponse;
import com.backend.stripewalletapi.dto.response.WalletTransactionResponse;
import com.backend.stripewalletapi.entity.Wallet;
import com.backend.stripewalletapi.entity.WalletTransaction;
import com.backend.stripewalletapi.enums.TransactionStatus;
import com.backend.stripewalletapi.enums.TransactionType;
import com.backend.stripewalletapi.event.BalanceUpdatedEvent;
import com.backend.stripewalletapi.event.TransactionCompletedEvent;
import com.backend.stripewalletapi.exception.WalletNotFoundException;
import com.backend.stripewalletapi.exception.WalletTransactionNotFoundException;
import com.backend.stripewalletapi.repository.WalletRepository;
import com.backend.stripewalletapi.repository.WalletTransactionRepository;
import com.backend.stripewalletapi.service.domain.WalletDomainService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final String ENDPOINT_TOP_UP   = "/wallet/top-up";
    private static final String ENDPOINT_TRANSFER = "/wallet/transfer";
    private static final String ENDPOINT_REFUND   = "/wallet/refund";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final StripeService stripeService;
    private final IdempotencyService idempotencyService;
    private final WalletDomainService walletDomainService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return new BalanceResponse(wallet.getBalance(), wallet.getCurrency(), wallet.getUpdatedAt());
    }

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "stripeService", fallbackMethod = "initiateTopUpFallback")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TopUpResponse initiateTopUp(UUID userId, TopUpRequest request,
                                       String idempotencyKey, String ipAddress) {
        Optional<String> cached = idempotencyService.findCachedResponse(idempotencyKey, userId, ENDPOINT_TOP_UP);
        if (cached.isPresent()) return deserialize(cached.get(), TopUpResponse.class);

        Wallet wallet = getWalletByUserId(userId);
        CheckoutSessionResult checkout = stripeService.createCheckoutSession(userId, wallet.getId(), request.getAmount(), idempotencyKey);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet).type(TransactionType.TOP_UP).status(TransactionStatus.PENDING)
                .amount(request.getAmount()).idempotencyKey(idempotencyKey).stripeSessionId(checkout.sessionId())
                .description("Wallet top-up initiated").build();
        walletTransactionRepository.save(tx);

        eventPublisher.publishEvent(new TransactionCompletedEvent(this, userId, tx.getId(), "TOP_UP_INITIATED", ipAddress));

        TopUpResponse response = TopUpResponse.builder()
                .checkoutUrl(checkout.url()).transactionId(tx.getId())
                .message("Checkout created").build();

        idempotencyService.saveResponse(idempotencyKey, userId, ENDPOINT_TOP_UP, serialize(response));
        return response;
    }

    @Transactional
    public void markTransactionFailed(String stripePaymentIntentId, String reason) {
        walletTransactionRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .ifPresent(tx -> {
                    if (tx.getStatus() == TransactionStatus.PENDING) {
                        tx.setStatus(TransactionStatus.FAILED);
                        tx.setDescription(tx.getDescription() + " | Failure: " + reason);
                        walletTransactionRepository.save(tx);
                    }
                });
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void completeTopUp(String stripeSessionId, String stripePaymentIntentId,
                               UUID userId, BigDecimal stripeAmount) {

        Optional<WalletTransaction> existing = walletTransactionRepository.findByStripeSessionId(stripeSessionId);
        if (existing.isPresent() && existing.get().getStatus() == TransactionStatus.COMPLETED) return;

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId).orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        WalletTransaction tx = existing.orElseGet(() -> WalletTransaction.builder()
                .wallet(wallet).type(TransactionType.TOP_UP).status(TransactionStatus.PENDING)
                .amount(stripeAmount).stripeSessionId(stripeSessionId).description("Stripe top-up").build());

        if (tx.getAmount().compareTo(stripeAmount) != 0) {
            tx.setStatus(TransactionStatus.FAILED);
            walletTransactionRepository.save(tx);
            throw new RuntimeException("Amount mismatch");
        }

        walletDomainService.credit(wallet, tx.getAmount());
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setStripePaymentIntentId(stripePaymentIntentId);

        walletRepository.save(wallet);
        walletTransactionRepository.save(tx);

        outboxService.saveEvent("Wallet", wallet.getId().toString(), "BALANCE_CREDITED", tx);

        eventPublisher.publishEvent(new TransactionCompletedEvent(this, userId, tx.getId(), "TOP_UP_COMPLETED", null));
        eventPublisher.publishEvent(new BalanceUpdatedEvent(this, userId, wallet.getBalance(), tx.getAmount()));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletTransactionResponse transfer(UUID senderId, TransferRequest request,
                                              String idempotencyKey, String ipAddress) {
        Optional<String> cached = idempotencyService.findCachedResponse(idempotencyKey, senderId, ENDPOINT_TRANSFER);
        if (cached.isPresent()) return deserialize(cached.get(), WalletTransactionResponse.class);

        if (senderId.equals(request.getToUserId())) throw new IllegalArgumentException("Self-transfer prohibited");

        UUID firstId = senderId.compareTo(request.getToUserId()) < 0 ? senderId : request.getToUserId();
        UUID secondId = firstId.equals(senderId) ? request.getToUserId() : senderId;

        Wallet firstWallet = walletRepository.findByUserIdForUpdate(firstId).orElseThrow(() -> new WalletNotFoundException("Wallet not found"));
        Wallet secondWallet = walletRepository.findByUserIdForUpdate(secondId).orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        Wallet senderWallet = firstWallet.getUserId().equals(senderId) ? firstWallet : secondWallet;
        Wallet receiverWallet = (senderWallet == firstWallet) ? secondWallet : firstWallet;

        walletDomainService.debit(senderWallet, request.getAmount());
        walletDomainService.credit(receiverWallet, request.getAmount());

        walletRepository.saveAll(List.of(senderWallet, receiverWallet));

        WalletTransaction senderTx = WalletTransaction.builder()
                .wallet(senderWallet).type(TransactionType.TRANSFER_OUT).status(TransactionStatus.COMPLETED)
                .amount(request.getAmount().negate()).idempotencyKey(idempotencyKey)
                .description(request.getDescription() != null ? request.getDescription() : "Transfer").build();

        WalletTransaction receiverTx = WalletTransaction.builder()
                .wallet(receiverWallet).type(TransactionType.TRANSFER_IN).status(TransactionStatus.COMPLETED)
                .amount(request.getAmount()).description("Transfer from " + senderId).build();

        walletTransactionRepository.saveAll(List.of(senderTx, receiverTx));
        senderTx.setRelatedTransactionId(receiverTx.getId());
        receiverTx.setRelatedTransactionId(senderTx.getId());
        walletTransactionRepository.saveAll(List.of(senderTx, receiverTx));

        outboxService.saveEvent("Wallet", senderWallet.getId().toString(), "BALANCE_DEBITED", senderTx);
        outboxService.saveEvent("Wallet", receiverWallet.getId().toString(), "BALANCE_CREDITED", receiverTx);

        eventPublisher.publishEvent(new TransactionCompletedEvent(this, senderId, senderTx.getId(), "TRANSFER_COMPLETED", ipAddress));
        eventPublisher.publishEvent(new BalanceUpdatedEvent(this, senderId, senderWallet.getBalance(), request.getAmount().negate()));
        eventPublisher.publishEvent(new BalanceUpdatedEvent(this, request.getToUserId(), receiverWallet.getBalance(), request.getAmount()));

        WalletTransactionResponse response = mapToResponse(senderTx);
        idempotencyService.saveResponse(idempotencyKey, senderId, ENDPOINT_TRANSFER, serialize(response));
        return response;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletTransactionResponse refund(UUID userId, RefundRequest request,
                                            String idempotencyKey, String ipAddress) {
        Optional<String> cached = idempotencyService.findCachedResponse(idempotencyKey, userId, ENDPOINT_REFUND);
        if (cached.isPresent()) return deserialize(cached.get(), WalletTransactionResponse.class);

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId).orElseThrow(() -> new WalletNotFoundException("Wallet not found"));
        WalletTransaction original = walletTransactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new WalletTransactionNotFoundException("Transaction not found"));

        if (!original.getWallet().getUserId().equals(userId)) throw new AccessDeniedException("Access denied");
        if (original.getType() != TransactionType.TOP_UP || original.getStatus() != TransactionStatus.COMPLETED)
            throw new IllegalArgumentException("Invalid refund state");

        walletDomainService.debit(wallet, original.getAmount());
        original.setStatus(TransactionStatus.REFUNDED);
        walletTransactionRepository.save(original);

        WalletTransaction refundTx = WalletTransaction.builder()
                .wallet(wallet).type(TransactionType.REFUND).status(TransactionStatus.COMPLETED)
                .amount(original.getAmount().negate()).idempotencyKey(idempotencyKey)
                .relatedTransactionId(original.getId()).description("Refund for " + original.getId()).build();
        walletTransactionRepository.save(refundTx);

        outboxService.saveEvent("Wallet", wallet.getId().toString(), "BALANCE_REFUNDED", refundTx);

        eventPublisher.publishEvent(new TransactionCompletedEvent(this, userId, refundTx.getId(), "REFUND_COMPLETED", ipAddress));
        eventPublisher.publishEvent(new BalanceUpdatedEvent(this, userId, wallet.getBalance(), original.getAmount().negate()));

        WalletTransactionResponse response = mapToResponse(refundTx);
        idempotencyService.saveResponse(idempotencyKey, userId, ENDPOINT_REFUND, serialize(response));
        return response;
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(UUID userId, TransactionStatus status, Pageable pageable) {
        Wallet wallet = getWalletByUserId(userId);
        Page<WalletTransaction> page = (status != null) 
                ? walletTransactionRepository.findByWalletAndStatusOrderByCreatedAtDesc(wallet, status, pageable)
                : walletTransactionRepository.findByWalletOrderByCreatedAtDesc(wallet, pageable);
        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getAllTransactionsForAdmin(Pageable pageable) {
        return walletTransactionRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public String exportTransactionsToCsv(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        List<WalletTransaction> txs = walletTransactionRepository.findByWalletOrderByCreatedAtDesc(wallet, Pageable.unpaged()).getContent();
        StringBuilder csv = new StringBuilder("ID,Type,Status,Amount,Description,Date\n");
        for (WalletTransaction tx : txs) csv.append(String.format("%s,%s,%s,%s,\"%s\",%s\n", tx.getId(), tx.getType(), tx.getStatus(), tx.getAmount(), tx.getDescription().replace("\"", "'"), tx.getCreatedAt()));
        return csv.toString();
    }

    private Wallet getWalletByUserId(UUID userId) {
        return walletRepository.findByUserId(userId).orElseThrow(() -> new WalletNotFoundException("Wallet not found"));
    }

    private WalletTransactionResponse mapToResponse(WalletTransaction tx) {
        return WalletTransactionResponse.builder().id(tx.getId()).type(tx.getType()).status(tx.getStatus()).amount(tx.getAmount()).description(tx.getDescription()).relatedTransactionId(tx.getRelatedTransactionId()).createdAt(tx.getCreatedAt()).build();
    }

    private String serialize(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private <T> T deserialize(String json, Class<T> clazz) {
        try { return objectMapper.readValue(json, clazz); } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    public TopUpResponse initiateTopUpFallback(UUID userId, TopUpRequest request, String idempotencyKey, String ipAddress, Throwable t) {
        log.error("Stripe service is down or slow. Fallback triggered for user {}: {}", userId, t.getMessage());
        return TopUpResponse.builder()
                .message("Payment provider is currently unavailable. Please try again in a few minutes.")
                .build();
    }
}
