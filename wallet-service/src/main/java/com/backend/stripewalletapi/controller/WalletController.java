package com.backend.stripewalletapi.controller;

import com.backend.stripewalletapi.dto.request.RefundRequest;
import com.backend.stripewalletapi.dto.request.TopUpRequest;
import com.backend.stripewalletapi.dto.request.TransferRequest;
import com.backend.stripewalletapi.dto.response.ApiResponse;
import com.backend.stripewalletapi.dto.response.BalanceResponse;
import com.backend.stripewalletapi.dto.response.TopUpResponse;
import com.backend.stripewalletapi.dto.response.WalletTransactionResponse;
import com.backend.stripewalletapi.enums.TransactionStatus;
import com.backend.stripewalletapi.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * WalletController: High-performance microservice controller.
 * Senior pattern: Uses userId directly from JWT (passed via Principal).
 * No Repository dependency for maximum decoupling.
 */
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet operations")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/balance")
    @Operation(summary = "Get wallet balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getBalance(UUID.fromString(userId))));
    }

    @PostMapping("/top-up")
    @Operation(summary = "Initiate wallet top-up")
    public ResponseEntity<ApiResponse<TopUpResponse>> topUp(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody TopUpRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        TopUpResponse response = walletService.initiateTopUp(UUID.fromString(userId), request, idempotencyKey, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> transfer(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        WalletTransactionResponse response = walletService.transfer(UUID.fromString(userId), request, idempotencyKey, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Transfer completed", response));
    }

    @PostMapping("/refund")
    @Operation(summary = "Refund top-up")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> refund(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        WalletTransactionResponse response = walletService.refund(UUID.fromString(userId), request, idempotencyKey, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Refund processed", response));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get transactions")
    public ResponseEntity<ApiResponse<Page<WalletTransactionResponse>>> getTransactions(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] sortParts = sort.split(",");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(
                sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortParts[0]));
        return ResponseEntity.ok(ApiResponse.success(walletService.getTransactions(UUID.fromString(userId), status, pageable)));
    }

    @GetMapping("/transactions/export")
    public ResponseEntity<byte[]> exportTransactions(@AuthenticationPrincipal String userId) {
        String csv = walletService.exportTransactionsToCsv(UUID.fromString(userId));
        byte[] content = csv.getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }

    @GetMapping("/admin/transactions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<WalletTransactionResponse>>> getAllTransactionsForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(walletService.getAllTransactionsForAdmin(pageable)));
    }

    @GetMapping("/top-up/success")
    public ResponseEntity<ApiResponse<String>> topUpSuccess(@RequestParam("session_id") String sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Payment successful!", sessionId));
    }

    @GetMapping("/top-up/cancel")
    public ResponseEntity<ApiResponse<String>> topUpCancel() {
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled.", null));
    }
}
