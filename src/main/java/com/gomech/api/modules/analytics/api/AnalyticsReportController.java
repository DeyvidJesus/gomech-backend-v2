package com.gomech.api.modules.analytics.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.application.AnalyticsReportService;
import com.gomech.api.modules.analytics.domain.AggregationInterval;
import com.gomech.api.modules.analytics.domain.ExportFormat;
import com.gomech.api.modules.analytics.domain.ReportType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics - Reports & Exports", description = "Geração de relatórios parametrizados e exportação de dados")
public class AnalyticsReportController {

    private final AnalyticsReportService reportService;

    @GetMapping
    @PreAuthorize("hasAuthority('ANALYTICS_REPORT_READ') or hasRole('Proprietário')")
    @Operation(summary = "Gerar relatório parametrizado", description = "Gera visualização tabular detalhada com filtros e paginação.")
    public ResponseEntity<AnalyticsDtos.ReportResponse> generateReport(
            @RequestParam(name = "reportType", defaultValue = "OPERATIONAL_SUMMARY") ReportType reportType,
            @RequestParam(name = "unitId", required = false) UUID unitId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "interval", required = false) AggregationInterval interval,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getTenantId();
        AnalyticsDtos.ReportQueryRequest request = AnalyticsDtos.ReportQueryRequest.builder()
                .reportType(reportType)
                .unitId(unitId)
                .startDate(startDate)
                .endDate(endDate)
                .interval(interval)
                .search(search)
                .build();

        AnalyticsDtos.ReportResponse response = reportService.generateReport(tenantId, request, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/export")
    @PreAuthorize("hasAuthority('ANALYTICS_REPORT_EXPORT') or hasRole('Proprietário')")
    @Operation(summary = "Exportar relatório em arquivo", description = "Exporta relatório no formato CSV ou JSON consumindo cota de relatório da assinatura.")
    public ResponseEntity<byte[]> exportReport(@RequestBody AnalyticsDtos.ExportRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        byte[] data = reportService.exportReport(tenantId, request);

        ExportFormat format = request.format() != null ? request.format() : ExportFormat.CSV;
        String contentType = (format == ExportFormat.CSV) ? "text/csv; charset=UTF-8" : MediaType.APPLICATION_JSON_VALUE;
        String extension = (format == ExportFormat.CSV) ? "csv" : "json";
        String filename = "report-" + (request.reportType() != null ? request.reportType().name().toLowerCase() : "data") + "-" + LocalDate.now() + "." + extension;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(data);
    }
}
