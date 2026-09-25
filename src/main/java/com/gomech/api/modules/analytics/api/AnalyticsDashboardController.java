package com.gomech.api.modules.analytics.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.application.AnalyticsDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics - Executive Dashboard", description = "Painel executivo com métricas consolidadas e séries temporais")
public class AnalyticsDashboardController {

    private final AnalyticsDashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('ANALYTICS_DASHBOARD_READ') or hasRole('Proprietário')")
    @Operation(summary = "Obter visão consolidada do painel", description = "Retorna os principais KPIs, séries temporais e rankings consolidados por tenant e filial.")
    public ResponseEntity<AnalyticsDtos.DashboardSummaryResponse> getDashboardSummary(
            @RequestParam(name = "unitId", required = false) UUID unitId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        AnalyticsDtos.DashboardSummaryResponse response = dashboardService.getDashboardSummary(tenantId, unitId, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
