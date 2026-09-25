package com.gomech.api.modules.analytics.api;

import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.application.AnalyticsKpiCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics/kpis")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics - KPI Contracts & Catalog", description = "Catálogo com contratos formais de KPIs, fórmulas, unidades e dimensões")
public class AnalyticsKpiController {

    private final AnalyticsKpiCatalogService kpiCatalogService;

    @GetMapping
    @PreAuthorize("hasAuthority('ANALYTICS_KPI_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar catálogo de KPIs", description = "Retorna os contratos formais, definições, fórmulas e políticas de atualização de todos os KPIs do GoMech.")
    public ResponseEntity<List<AnalyticsDtos.KpiDefinitionDto>> listKpiCatalog() {
        return ResponseEntity.ok(kpiCatalogService.getCatalogDtos());
    }
}
