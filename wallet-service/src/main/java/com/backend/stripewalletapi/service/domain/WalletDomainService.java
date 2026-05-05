package com.backend.stripewalletapi.service.domain;

import com.backend.stripewalletapi.entity.Wallet;
import com.backend.stripewalletapi.exception.InsufficientFundsException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Domain Service: Pure business logic for Wallet operations.
 * This layer is independent of persistence, infrastructure, or orchestration.
 * Standard Senior Clean Architecture / DDD-lite pattern.
 */
@Service
public class WalletDomainService {

    /**
     * Credits the wallet balance.
     * Invariants: Balance must increase.
     */
    public void credit(Wallet wallet, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        
        BigDecimal oldBalance = wallet.getBalance();
        wallet.setBalance(oldBalance.add(amount));
        
        // Post-condition invariant check
        if (wallet.getBalance().compareTo(oldBalance) <= 0) {
            throw new IllegalStateException("Domain Invariant Violation: Balance must increase after credit");
        }
    }

    /**
     * Debits the wallet balance.
     * Invariants: Balance cannot be negative.
     */
    public void debit(Wallet wallet, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in domain model");
        }
        
        BigDecimal oldBalance = wallet.getBalance();
        wallet.setBalance(oldBalance.subtract(amount));
        
        // Post-condition invariant check
        if (wallet.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Domain Invariant Violation: Balance cannot be negative");
        }
    }
}
