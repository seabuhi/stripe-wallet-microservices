package com.backend.stripewalletapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopUpResponse {
    private String checkoutUrl;
    private UUID transactionId;
    private String message;
}
