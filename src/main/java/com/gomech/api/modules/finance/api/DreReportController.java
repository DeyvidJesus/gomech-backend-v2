package com.gomech.api.modules.finance.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.finance.api.dto.DreReportDtos;
import com.gomech.api.modules.finance.application.DreReportService;
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
@RequestMapping("/api/v1/finance/dre")
@RequiredArgsConstructor
@Tag(name = "Finance - DRE", description = "Demonstrativo do Resultado do Exercício e Lucratividade")
@SecurityRequirement(name = "bearerAuth")
public class DreReportController {

    private final DreReportService dreReportService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ') or hasRole('Proprietário') or hasRole('ADMIN')")
    @Operation(summary = "Gerar Demonstrativo do Resultado do Exercício (DRE)")
    public ResponseEntity<DreReportDtos.DreReport> getDre(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(dreReportService.generateDre(tenantId, startDate, endDate));
    }
}
