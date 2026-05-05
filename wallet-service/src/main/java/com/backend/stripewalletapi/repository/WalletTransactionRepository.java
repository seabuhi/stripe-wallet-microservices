package com.backend.stripewalletapi.repository;

import com.backend.stripewalletapi.entity.WalletTransaction;
import com.backend.stripewalletapi.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByStripeSessionId(String stripeSessionId);

    Optional<WalletTransaction> findByStripePaymentIntentId(String stripePaymentIntentId);

    Page<WalletTransaction> findAllByWalletUserId(UUID userId, Pageable pageable);

    Page<WalletTransaction> findAllByWalletUserIdAndStatus(UUID userId, TransactionStatus status, Pageable pageable);

    List<WalletTransaction> findAllByWalletUserId(UUID userId);
}
