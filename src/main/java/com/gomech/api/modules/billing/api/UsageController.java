package com.gomech.api.modules.billing.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.billing.api.dto.RecordUsageRequest;
import com.gomech.api.modules.billing.api.dto.UsageRecordResponse;
import com.gomech.api.modules.billing.application.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/usage")
@RequiredArgsConstructor
@Tag(name = "Billing - Consumo e Cotas", description = "Monitoramento e medição de recursos consumidos pelo Tenant")
@SecurityRequirement(name = "bearerAuth")
public class UsageController {

    private final UsageService usageService;

    @GetMapping
    @Operation(summary = "Listar consumo de recursos do Tenant no ciclo atual")
    public ResponseEntity<List<UsageRecordResponse>> getCurrentUsage() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(usageService.getCurrentPeriodUsageList(tenantId));
    }

    @PostMapping("/record")
    @Operation(summary = "Registrar consumo de recurso tarifado/controlado (ex: IA, WhatsApp, Armazenamento)")
    public ResponseEntity<Void> recordUsage(@Valid @RequestBody RecordUsageRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        usageService.recordUsage(tenantId, request.unitId(), request.dimension(), request.amount());
        return ResponseEntity.accepted().build();
    }
}
