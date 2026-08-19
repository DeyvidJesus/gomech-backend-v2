package com.gomech.api.modules.billing.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.billing.api.dto.ChangePlanRequest;
import com.gomech.api.modules.billing.api.dto.SubscriptionResponse;
import com.gomech.api.modules.billing.application.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/subscription")
@RequiredArgsConstructor
@Tag(name = "Billing - Assinatura", description = "Gestão da assinatura ativa, cotas e contratação de planos")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @Operation(summary = "Consultar dados da assinatura ativa do Tenant")
    public ResponseEntity<SubscriptionResponse> getSubscription() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(subscriptionService.getSubscription(tenantId));
    }

    @PostMapping("/change-plan")
    @PreAuthorize("hasAuthority('FINANCE_TRANSACTION_WRITE') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Alterar ou contratar um novo plano de assinatura para o Tenant")
    public ResponseEntity<SubscriptionResponse> changePlan(@Valid @RequestBody ChangePlanRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(subscriptionService.changePlan(tenantId, request));
    }
}
