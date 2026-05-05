package com.backend.stripewalletapi.service.domain;

import com.backend.stripewalletapi.entity.Wallet;
import com.backend.stripewalletapi.exception.InsufficientFundsException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WalletDomainService {

    public void credit(Wallet wallet, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        wallet.setBalance(wallet.getBalance().add(amount));
    }

    public void debit(Wallet wallet, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
    }

    public void transfer(Wallet sender, Wallet receiver, BigDecimal amount) {
        debit(sender, amount);
        credit(receiver, amount);
    }
}
