package com.backend.stripewalletapi.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RefundRequest {

    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;
}
