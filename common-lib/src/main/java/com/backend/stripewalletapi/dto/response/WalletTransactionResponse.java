package com.backend.stripewalletapi.dto.response;

import com.backend.stripewalletapi.enums.TransactionStatus;
import com.backend.stripewalletapi.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionResponse {
    private UUID id;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private String description;
    private UUID relatedTransactionId;
    private LocalDateTime createdAt;
}
