package com.backend.stripewalletapi.repository;

import com.backend.stripewalletapi.entity.Wallet;
import com.backend.stripewalletapi.entity.WalletTransaction;
import com.backend.stripewalletapi.enums.TransactionStatus;
import com.backend.stripewalletapi.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Page<WalletTransaction> findByWalletOrderByCreatedAtDesc(Wallet wallet, Pageable pageable);

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<WalletTransaction> findByStripeSessionId(String stripeSessionId);

    List<WalletTransaction> findByWalletAndTypeAndStatus(
            Wallet wallet, TransactionType type, TransactionStatus status);

    Optional<WalletTransaction> findByStripePaymentIntentId(String stripePaymentIntentId);

    Page<WalletTransaction> findByWalletAndStatusOrderByCreatedAtDesc(
            Wallet wallet, TransactionStatus status, Pageable pageable);

    // Admin view
    Page<WalletTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
