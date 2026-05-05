package com.backend.stripewalletapi.exception;

public class InvalidStripeSignatureException extends RuntimeException {
    public InvalidStripeSignatureException(String message) {
        super(message);
    }
}
