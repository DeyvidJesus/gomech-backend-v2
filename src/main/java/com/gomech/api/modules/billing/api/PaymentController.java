package com.gomech.api.modules.billing.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.billing.api.dto.PaymentDtos;
import com.gomech.api.modules.billing.application.PaymentInitiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/payments")
@RequiredArgsConstructor
@Tag(name = "Billing - Pagamentos & Checkout", description = "Iniciação de pagamentos via Pagar.me (PIX, Cartão e Boleto)")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentInitiationService paymentService;

    @PostMapping("/checkout")
    @PreAuthorize("hasAuthority('BILLING_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Iniciar pagamento ou assinatura via Pagar.me (PIX, Cartão, Boleto)")
    public ResponseEntity<PaymentDtos.PaymentResponse> checkout(
            @Valid @RequestBody PaymentDtos.InitiatePaymentRequest request
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.initiatePayment(tenantId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BILLING_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Listar histórico de faturas e pagamentos da oficina")
    public ResponseEntity<Page<PaymentDtos.PaymentResponse>> listPayments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(paymentService.listPayments(tenantId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BILLING_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Obter detalhes de um pagamento / fatura")
    public ResponseEntity<PaymentDtos.PaymentResponse> getPayment(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(paymentService.getPayment(id, tenantId));
    }
}
