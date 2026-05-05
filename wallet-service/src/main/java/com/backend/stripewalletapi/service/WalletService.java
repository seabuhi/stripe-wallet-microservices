package com.backend.stripewalletapi.service;

import com.backend.stripewalletapi.client.PaymentClient;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PaymentClient paymentClient;
    private final IdempotencyService idempotencyService;
    private final WalletDomainService walletDomainService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        BalanceResponse response = new BalanceResponse();
        response.setBalance(wallet.getBalance());
        response.setCurrency(wallet.getCurrency());
        response.setUpdatedAt(wallet.getUpdatedAt());
        return response;
    }

    @Transactional
    public TopUpResponse initiateTopUp(UUID userId, TopUpRequest request, String idempotencyKey, String ipAddress) {
        Optional<String> cached = idempotencyService.getResponse(idempotencyKey, "/wallet/top-up");
        if (cached.isPresent()) return deserialize(cached.get(), TopUpResponse.class);

        Wallet wallet = getWalletByUserId(userId);
        CheckoutSessionResult checkout = paymentClient.createCheckoutSession(userId, request.getAmount(), "usd");

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(TransactionType.TOP_UP);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setAmount(request.getAmount());
        tx.setCurrency("usd");
        tx.setStripeSessionId(checkout.getSessionId());
        tx.setDescription("Wallet top-up initiated");
        walletTransactionRepository.save(tx);

        eventPublisher.publishEvent(new TransactionCompletedEvent(this, userId, tx.getId(), "TOP_UP_INITIATED", ipAddress));

        TopUpResponse response = new TopUpResponse();
        response.setCheckoutUrl(checkout.getCheckoutUrl());
        response.setTransactionId(tx.getId());
        response.setStatus(TransactionStatus.PENDING);
        
        idempotencyService.saveResponse(idempotencyKey, "/wallet/top-up", serialize(response));
        return response;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void completeTopUp(String sessionId, String paymentIntentId, UUID userId, BigDecimal amount) {
        WalletTransaction tx = walletTransactionRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new WalletTransactionNotFoundException("Transaction not found for session: " + sessionId));

        if (tx.getStatus() == TransactionStatus.COMPLETED) return;

        Wallet wallet = tx.getWallet();
        walletDomainService.credit(wallet, amount);
        walletRepository.save(wallet);

        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setStripePaymentIntentId(paymentIntentId);
        walletTransactionRepository.save(tx);

        outboxService.saveEvent("Wallet", wallet.getId().toString(), "BALANCE_CREDITED", tx);
        eventPublisher.publishEvent(new TransactionCompletedEvent(this, userId, tx.getId(), "TOP_UP_COMPLETED", null));
        eventPublisher.publishEvent(new BalanceUpdatedEvent(this, userId, wallet.getBalance(), tx.getAmount()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markTransactionFailed(String paymentIntentId, String reason) {
        walletTransactionRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(tx -> {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setFailureReason(reason);
            walletTransactionRepository.save(tx);
        });
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletTransactionResponse transfer(UUID senderId, TransferRequest request, String idempotencyKey, String ipAddress) {
        Optional<String> cached = idempotencyService.getResponse(idempotencyKey, "/wallet/transfer");
        if (cached.isPresent()) return deserialize(cached.get(), WalletTransactionResponse.class);

        Wallet senderWallet = getWalletByUserId(senderId);
        Wallet receiverWallet = walletRepository.findByUserIdForUpdate(request.getToUserId())
                .orElseThrow(() -> new WalletNotFoundException("Receiver wallet not found"));

        walletDomainService.transfer(senderWallet, receiverWallet, request.getAmount());
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        WalletTransaction senderTx = new WalletTransaction();
        senderTx.setWallet(senderWallet);
        senderTx.setType(TransactionType.TRANSFER);
        senderTx.setStatus(TransactionStatus.COMPLETED);
        senderTx.setAmount(request.getAmount().negate());
        senderTx.setCurrency("usd");
        senderTx.setDescription("Transfer to " + request.getToUserId());
        
        WalletTransaction receiverTx = new WalletTransaction();
        receiverTx.setWallet(receiverWallet);
        receiverTx.setType(TransactionType.TRANSFER);
        receiverTx.setStatus(TransactionStatus.COMPLETED);
        receiverTx.setAmount(request.getAmount());
        receiverTx.setCurrency("usd");
        receiverTx.setDescription("Transfer from " + senderId);

        walletTransactionRepository.save(senderTx);
        walletTransactionRepository.save(receiverTx);

        outboxService.saveEvent("Wallet", senderWallet.getId().toString(), "BALANCE_DEBITED", senderTx);
        outboxService.saveEvent("Wallet", receiverWallet.getId().toString(), "BALANCE_CREDITED", receiverTx);

        eventPublisher.publishEvent(new TransactionCompletedEvent(this, senderId, senderTx.getId(), "TRANSFER_COMPLETED", ipAddress));
        eventPublisher.publishEvent(new BalanceUpdatedEvent(this, senderId, senderWallet.getBalance(), request.getAmount().negate()));
        eventPublisher.publishEvent(new BalanceUpdatedEvent(this, request.getToUserId(), receiverWallet.getBalance(), request.getAmount()));

        WalletTransactionResponse response = mapToResponse(senderTx);
        idempotencyService.saveResponse(idempotencyKey, "/wallet/transfer", serialize(response));
        return response;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletTransactionResponse refund(UUID userId, RefundRequest request, String idempotencyKey, String ipAddress) {
        Optional<String> cached = idempotencyService.getResponse(idempotencyKey, "/wallet/refund");
        if (cached.isPresent()) return deserialize(cached.get(), WalletTransactionResponse.class);

        WalletTransaction original = walletTransactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new WalletTransactionNotFoundException("Original transaction not found"));

        Wallet wallet = getWalletByUserId(userId);
        walletDomainService.debit(wallet, original.getAmount());
        walletRepository.save(wallet);

        WalletTransaction refundTx = new WalletTransaction();
        refundTx.setWallet(wallet);
        refundTx.setType(TransactionType.REFUND);
        refundTx.setStatus(TransactionStatus.COMPLETED);
        refundTx.setAmount(original.getAmount().negate());
        refundTx.setCurrency("usd");
        refundTx.setDescription("Refund of " + original.getId());
        walletTransactionRepository.save(refundTx);

        outboxService.saveEvent("Wallet", wallet.getId().toString(), "BALANCE_REFUNDED", refundTx);
        eventPublisher.publishEvent(new TransactionCompletedEvent(this, userId, refundTx.getId(), "REFUND_COMPLETED", ipAddress));
        eventPublisher.publishEvent(new BalanceUpdatedEvent(this, userId, wallet.getBalance(), original.getAmount().negate()));

        WalletTransactionResponse response = mapToResponse(refundTx);
        idempotencyService.saveResponse(idempotencyKey, "/wallet/refund", serialize(response));
        return response;
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(UUID userId, TransactionStatus status, Pageable pageable) {
        Page<WalletTransaction> page = (status == null) 
                ? walletTransactionRepository.findAllByWalletUserId(userId, pageable)
                : walletTransactionRepository.findAllByWalletUserIdAndStatus(userId, status, pageable);
        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public String exportTransactionsToCsv(UUID userId) {
        List<WalletTransaction> transactions = walletTransactionRepository.findAllByWalletUserId(userId);
        StringBuilder csv = new StringBuilder("ID,Type,Status,Amount,Currency,CreatedAt\n");
        for (WalletTransaction tx : transactions) {
            csv.append(tx.getId()).append(",")
               .append(tx.getType()).append(",")
               .append(tx.getStatus()).append(",")
               .append(tx.getAmount()).append(",")
               .append(tx.getCurrency()).append(",")
               .append(tx.getCreatedAt()).append("\n");
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getAllTransactionsForAdmin(Pageable pageable) {
        return walletTransactionRepository.findAll(pageable).map(this::mapToResponse);
    }

    private Wallet getWalletByUserId(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));
    }

    private WalletTransactionResponse mapToResponse(WalletTransaction tx) {
        WalletTransactionResponse response = new WalletTransactionResponse();
        response.setId(tx.getId());
        response.setType(tx.getType());
        response.setStatus(tx.getStatus());
        response.setAmount(tx.getAmount());
        response.setCurrency(tx.getCurrency());
        response.setDescription(tx.getDescription());
        response.setCreatedAt(tx.getCreatedAt());
        return response;
    }

    private String serialize(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("Serialization error"); }
    }

    private <T> T deserialize(String json, Class<T> clazz) {
        try { return objectMapper.readValue(json, clazz); }
        catch (JsonProcessingException e) { throw new RuntimeException("Deserialization error"); }
    }
}
