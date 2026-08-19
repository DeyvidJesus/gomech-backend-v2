package com.gomech.api.modules.billing.api;

import com.gomech.api.modules.billing.api.dto.BillingPlanResponse;
import com.gomech.api.modules.billing.application.BillingPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing/plans")
@RequiredArgsConstructor
@Tag(name = "Billing - Planos", description = "Consulta ao catálogo de planos e limites de assinatura do GoMech")
public class BillingPlanController {

    private final BillingPlanService billingPlanService;

    @GetMapping
    @Operation(summary = "Listar planos disponíveis para contratação e limites de recursos")
    public ResponseEntity<List<BillingPlanResponse>> getAvailablePlans() {
        return ResponseEntity.ok(billingPlanService.getAvailablePlans());
    }
}
