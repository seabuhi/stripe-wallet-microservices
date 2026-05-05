package com.backend.stripewalletapi.exception;

public class WalletTransactionNotFoundException extends RuntimeException {
    public WalletTransactionNotFoundException(String message) {
        super(message);
    }
}
