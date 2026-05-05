package com.backend.stripewalletapi.controller;

import com.backend.stripewalletapi.dto.request.RefundRequest;
import com.backend.stripewalletapi.dto.request.TopUpRequest;
import com.backend.stripewalletapi.dto.request.TransferRequest;
import com.backend.stripewalletapi.dto.response.ApiResponse;
import com.backend.stripewalletapi.dto.response.BalanceResponse;
import com.backend.stripewalletapi.dto.response.TopUpResponse;
import com.backend.stripewalletapi.dto.response.WalletTransactionResponse;
import com.backend.stripewalletapi.enums.TransactionStatus;
import com.backend.stripewalletapi.repository.UserRepository;
import com.backend.stripewalletapi.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet balance, top-up, transfer, refund and transaction history")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @GetMapping("/balance")
    @Operation(summary = "Get wallet balance", description = "Returns current USD balance and last update timestamp")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(walletService.getBalance(userId)));
    }

    @PostMapping("/top-up")
    @Operation(summary = "Initiate wallet top-up", description = "Creates a Stripe Checkout session. Redirects user to checkoutUrl.")
    public ResponseEntity<ApiResponse<TopUpResponse>> topUp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TopUpRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        UUID userId = resolveUserId(userDetails);
        TopUpResponse response = walletService.initiateTopUp(userId, request, idempotencyKey, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds to another user", description = "Atomically debits sender and credits receiver.")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> transfer(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        UUID userId = resolveUserId(userDetails);
        WalletTransactionResponse response = walletService.transfer(userId, request, idempotencyKey, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Transfer completed successfully", response));
    }

    @PostMapping("/refund")
    @Operation(summary = "Refund a completed top-up", description = "Refunds a completed top-up transaction.")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> refund(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        UUID userId = resolveUserId(userDetails);
        WalletTransactionResponse response = walletService.refund(userId, request, idempotencyKey, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Refund processed successfully", response));
    }

    /**
     * FIX: Added status filtering and pagination
     */
    @GetMapping("/transactions")
    @Operation(summary = "Get transaction history", description = "Paginated list of all wallet transactions with status filter.")
    public ResponseEntity<ApiResponse<Page<WalletTransactionResponse>>> getTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        UUID userId = resolveUserId(userDetails);
        String[] sortParts = sort.split(",");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(
                sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortParts[0]));
        return ResponseEntity.ok(ApiResponse.success(walletService.getTransactions(userId, status, pageable)));
    }

    /**
     * BONUS: Export CSV
     */
    @GetMapping("/transactions/export")
    @Operation(summary = "Export transactions to CSV", description = "Downloads all transactions for the current user as a CSV file.")
    public ResponseEntity<byte[]> exportTransactions(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        String csv = walletService.exportTransactionsToCsv(userId);
        byte[] content = csv.getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }

    /**
     * BONUS: Admin Global View
     */
    @GetMapping("/admin/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Get all transactions", description = "Admin only: View every transaction in the system.")
    public ResponseEntity<ApiResponse<Page<WalletTransactionResponse>>> getAllTransactionsForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(walletService.getAllTransactionsForAdmin(pageable)));
    }

    @GetMapping("/top-up/success")
    @Operation(summary = "Stripe success redirect")
    public ResponseEntity<ApiResponse<String>> topUpSuccess(@RequestParam("session_id") String sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Payment successful!", sessionId));
    }

    @GetMapping("/top-up/cancel")
    @Operation(summary = "Stripe cancel redirect")
    public ResponseEntity<ApiResponse<String>> topUpCancel() {
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled.", null));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"))
                .getId();
    }
}
