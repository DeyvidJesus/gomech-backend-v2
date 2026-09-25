package com.gomech.api.modules.analytics.application;

import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.domain.AnalyticsException;
import com.gomech.api.modules.analytics.domain.ExportFormat;
import com.gomech.api.modules.analytics.domain.ReportType;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsFinancialProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsInventoryProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsToolProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsWorkOrderProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsFinancialProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsInventoryProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsToolProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsWorkOrderProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsReportService {

    private final AnalyticsWorkOrderProjectionRepository workOrderProjectionRepository;
    private final AnalyticsFinancialProjectionRepository financialProjectionRepository;
    private final AnalyticsInventoryProjectionRepository inventoryProjectionRepository;
    private final AnalyticsToolProjectionRepository toolProjectionRepository;
    private final EntitlementService entitlementService;

    @Transactional(readOnly = true)
    public AnalyticsDtos.ReportResponse generateReport(
            UUID tenantId,
            AnalyticsDtos.ReportQueryRequest request,
            Pageable pageable
    ) {
        ReportType type = request.reportType() != null ? request.reportType() : ReportType.OPERATIONAL_SUMMARY;
        LocalDate end = (request.endDate() != null) ? request.endDate() : LocalDate.now();
        LocalDate start = (request.startDate() != null) ? request.startDate() : end.minusDays(30);

        OffsetDateTime startDt = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDt = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        return switch (type) {
            case OPERATIONAL_SUMMARY, EXECUTIVE_OVERVIEW -> buildOperationalReport(tenantId, request.unitId(), start, end, startDt, endDt, pageable);
            case FINANCIAL_PERFORMANCE -> buildFinancialReport(tenantId, request.unitId(), start, end, startDt, endDt, pageable);
            case INVENTORY_FLOW -> buildInventoryReport(tenantId, request.unitId(), start, end, startDt, endDt, pageable);
            case TOOL_EFFICIENCY -> buildToolReport(tenantId, request.unitId(), start, end, startDt, endDt, pageable);
            case BILLING_USAGE -> buildOperationalReport(tenantId, request.unitId(), start, end, startDt, endDt, pageable);
        };
    }

    @Transactional
    public byte[] exportReport(UUID tenantId, AnalyticsDtos.ExportRequest request) {
        // Avaliação e débito de cota via Core Entitlement
        QuotaDecision quotaDecision = entitlementService.checkQuota(tenantId, QuotaDimension.REPORTS, 1);
        if (!quotaDecision.allowed()) {
            throw new AnalyticsException("Limite de relatórios e exportações excedido para o seu plano: " + quotaDecision.reason());
        }

        AnalyticsDtos.ReportQueryRequest queryRequest = AnalyticsDtos.ReportQueryRequest.builder()
                .reportType(request.reportType())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .unitId(request.unitId())
                .interval(request.interval())
                .build();

        AnalyticsDtos.ReportResponse report = generateReport(tenantId, queryRequest, Pageable.unpaged());

        // Registrar consumo na cota do plano
        entitlementService.recordUsage(tenantId, QuotaDimension.REPORTS, 1);

        ExportFormat format = (request.format() != null) ? request.format() : ExportFormat.CSV;
        if (format == ExportFormat.CSV) {
            return generateCsvBytes(report);
        } else {
            return generateJsonBytes(report);
        }
    }

    private AnalyticsDtos.ReportResponse buildOperationalReport(
            UUID tenantId, UUID unitId, LocalDate start, LocalDate end, OffsetDateTime startDt, OffsetDateTime endDt, Pageable pageable
    ) {
        List<AnalyticsWorkOrderProjection> all = (unitId != null)
                ? workOrderProjectionRepository.findAllByTenantIdAndUnitIdAndOpenedAtBetween(tenantId, unitId, startDt, endDt)
                : workOrderProjectionRepository.findAllByTenantIdAndOpenedAtBetween(tenantId, startDt, endDt);

        int total = all.size();
        List<AnalyticsWorkOrderProjection> pagedList = paginate(all, pageable);

        List<AnalyticsDtos.ReportDataRow> rows = pagedList.stream()
                .map(w -> AnalyticsDtos.ReportDataRow.builder()
                        .id(w.getId().toString())
                        .date(w.getOpenedAt().toLocalDate())
                        .dimension("OPERATIONS")
                        .referenceCode(w.getOrderNumber() != null ? w.getOrderNumber() : w.getWorkOrderId().toString().substring(0, 8))
                        .category("Ordem de Serviço")
                        .description("OS #" + (w.getOrderNumber() != null ? w.getOrderNumber() : "") + " (" + w.getStatus() + ")")
                        .status(w.getStatus())
                        .quantity(BigDecimal.valueOf(w.getItemCount() != null ? w.getItemCount() : 1))
                        .primaryAmount(w.getTotalAmount())
                        .secondaryAmount(w.getServicesAmount())
                        .extraAttributes(Map.of(
                                "turnaroundHours", w.getTurnaroundHours() != null ? w.getTurnaroundHours() : BigDecimal.ZERO,
                                "partsAmount", w.getPartsAmount() != null ? w.getPartsAmount() : BigDecimal.ZERO
                        ))
                        .build())
                .toList();

        BigDecimal totalSum = all.stream().map(AnalyticsWorkOrderProjection::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsDtos.ReportResponse.builder()
                .reportType(ReportType.OPERATIONAL_SUMMARY)
                .tenantId(tenantId)
                .unitId(unitId)
                .startDate(start)
                .endDate(end)
                .summaryMetrics(Map.of(
                        "totalCount", total,
                        "totalAmount", totalSum,
                        "completedCount", all.stream().filter(w -> "COMPLETED".equalsIgnoreCase(w.getStatus())).count()
                ))
                .columnHeaders(List.of("Data", "OS #", "Status", "Itens", "Total (R$)", "Mão de Obra (R$)", "Peças (R$)"))
                .rows(rows)
                .totalElements(total)
                .totalPages(pageable.isPaged() ? (int) Math.ceil((double) total / pageable.getPageSize()) : 1)
                .currentPage(pageable.isPaged() ? pageable.getPageNumber() : 0)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    private AnalyticsDtos.ReportResponse buildFinancialReport(
            UUID tenantId, UUID unitId, LocalDate start, LocalDate end, OffsetDateTime startDt, OffsetDateTime endDt, Pageable pageable
    ) {
        List<AnalyticsFinancialProjection> all = (unitId != null)
                ? financialProjectionRepository.findAllByTenantIdAndUnitIdAndOccurredAtBetween(tenantId, unitId, startDt, endDt)
                : financialProjectionRepository.findAllByTenantIdAndOccurredAtBetween(tenantId, startDt, endDt);

        int total = all.size();
        List<AnalyticsFinancialProjection> pagedList = paginate(all, pageable);

        List<AnalyticsDtos.ReportDataRow> rows = pagedList.stream()
                .map(f -> AnalyticsDtos.ReportDataRow.builder()
                        .id(f.getId().toString())
                        .date(f.getOccurredAt().toLocalDate())
                        .dimension("FINANCE")
                        .referenceCode(f.getRecordType() + "-" + f.getRecordId().toString().substring(0, 8))
                        .category(f.getCategoryName() != null ? f.getCategoryName() : f.getRecordType())
                        .description(f.getType() + " (" + f.getRecordType() + ")")
                        .status(f.getStatus())
                        .quantity(BigDecimal.ONE)
                        .primaryAmount(f.getAmount())
                        .secondaryAmount(BigDecimal.ZERO)
                        .extraAttributes(Map.of(
                                "type", f.getType(),
                                "dueDate", f.getDueDate() != null ? f.getDueDate().toString() : "-",
                                "paidDate", f.getPaidDate() != null ? f.getPaidDate().toString() : "-"
                        ))
                        .build())
                .toList();

        BigDecimal credits = all.stream().filter(f -> "CREDIT".equalsIgnoreCase(f.getType())).map(AnalyticsFinancialProjection::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debits = all.stream().filter(f -> "DEBIT".equalsIgnoreCase(f.getType())).map(AnalyticsFinancialProjection::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsDtos.ReportResponse.builder()
                .reportType(ReportType.FINANCIAL_PERFORMANCE)
                .tenantId(tenantId)
                .unitId(unitId)
                .startDate(start)
                .endDate(end)
                .summaryMetrics(Map.of(
                        "totalCount", total,
                        "totalCredits", credits,
                        "totalDebits", debits,
                        "netBalance", credits.subtract(debits)
                ))
                .columnHeaders(List.of("Data", "Lançamento", "Categoria", "Tipo", "Status", "Valor (R$)"))
                .rows(rows)
                .totalElements(total)
                .totalPages(pageable.isPaged() ? (int) Math.ceil((double) total / pageable.getPageSize()) : 1)
                .currentPage(pageable.isPaged() ? pageable.getPageNumber() : 0)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    private AnalyticsDtos.ReportResponse buildInventoryReport(
            UUID tenantId, UUID unitId, LocalDate start, LocalDate end, OffsetDateTime startDt, OffsetDateTime endDt, Pageable pageable
    ) {
        List<AnalyticsInventoryProjection> all = (unitId != null)
                ? inventoryProjectionRepository.findAllByTenantIdAndUnitIdAndOccurredAtBetween(tenantId, unitId, startDt, endDt)
                : inventoryProjectionRepository.findAllByTenantIdAndOccurredAtBetween(tenantId, startDt, endDt);

        int total = all.size();
        List<AnalyticsInventoryProjection> pagedList = paginate(all, pageable);

        List<AnalyticsDtos.ReportDataRow> rows = pagedList.stream()
                .map(i -> AnalyticsDtos.ReportDataRow.builder()
                        .id(i.getId().toString())
                        .date(i.getOccurredAt().toLocalDate())
                        .dimension("INVENTORY")
                        .referenceCode(i.getMovementType())
                        .category("Estoque")
                        .description(i.getProductName() != null ? i.getProductName() : "Item " + i.getProductId())
                        .status(i.getMovementType())
                        .quantity(i.getQuantity())
                        .primaryAmount(i.getTotalValue())
                        .secondaryAmount(i.getUnitPrice())
                        .extraAttributes(Map.of("movementType", i.getMovementType()))
                        .build())
                .toList();

        BigDecimal totalSpend = all.stream().map(AnalyticsInventoryProjection::getTotalValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsDtos.ReportResponse.builder()
                .reportType(ReportType.INVENTORY_FLOW)
                .tenantId(tenantId)
                .unitId(unitId)
                .startDate(start)
                .endDate(end)
                .summaryMetrics(Map.of("totalCount", total, "totalValue", totalSpend))
                .columnHeaders(List.of("Data", "Movimentação", "Produto", "Quantidade", "Valor Unitário (R$)", "Total (R$)"))
                .rows(rows)
                .totalElements(total)
                .totalPages(pageable.isPaged() ? (int) Math.ceil((double) total / pageable.getPageSize()) : 1)
                .currentPage(pageable.isPaged() ? pageable.getPageNumber() : 0)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    private AnalyticsDtos.ReportResponse buildToolReport(
            UUID tenantId, UUID unitId, LocalDate start, LocalDate end, OffsetDateTime startDt, OffsetDateTime endDt, Pageable pageable
    ) {
        List<AnalyticsToolProjection> all = (unitId != null)
                ? toolProjectionRepository.findAllByTenantIdAndUnitIdAndOccurredAtBetween(tenantId, unitId, startDt, endDt)
                : toolProjectionRepository.findAllByTenantIdAndOccurredAtBetween(tenantId, startDt, endDt);

        int total = all.size();
        List<AnalyticsToolProjection> pagedList = paginate(all, pageable);

        List<AnalyticsDtos.ReportDataRow> rows = pagedList.stream()
                .map(t -> AnalyticsDtos.ReportDataRow.builder()
                        .id(t.getId().toString())
                        .date(t.getOccurredAt().toLocalDate())
                        .dimension("TOOLS")
                        .referenceCode(t.getToolId().toString().substring(0, 8))
                        .category("Ferramentas")
                        .description(t.getEventType())
                        .status(t.getEventType())
                        .quantity(BigDecimal.ONE)
                        .primaryAmount(t.getCost())
                        .secondaryAmount(t.getDowntimeHours())
                        .extraAttributes(Map.of("downtimeHours", t.getDowntimeHours() != null ? t.getDowntimeHours() : BigDecimal.ZERO))
                        .build())
                .toList();

        BigDecimal totalCost = all.stream().map(AnalyticsToolProjection::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsDtos.ReportResponse.builder()
                .reportType(ReportType.TOOL_EFFICIENCY)
                .tenantId(tenantId)
                .unitId(unitId)
                .startDate(start)
                .endDate(end)
                .summaryMetrics(Map.of("totalCount", total, "totalCost", totalCost))
                .columnHeaders(List.of("Data", "Ferramenta", "Evento", "Custo (R$)", "Downtime (h)"))
                .rows(rows)
                .totalElements(total)
                .totalPages(pageable.isPaged() ? (int) Math.ceil((double) total / pageable.getPageSize()) : 1)
                .currentPage(pageable.isPaged() ? pageable.getPageNumber() : 0)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    private <T> List<T> paginate(List<T> items, Pageable pageable) {
        if (!pageable.isPaged()) return items;
        int fromIndex = (int) pageable.getOffset();
        if (fromIndex >= items.size()) return Collections.emptyList();
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), items.size());
        return items.subList(fromIndex, toIndex);
    }

    private byte[] generateCsvBytes(AnalyticsDtos.ReportResponse report) {
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append(String.join(";", report.columnHeaders())).append("\n");
        // Rows
        for (AnalyticsDtos.ReportDataRow row : report.rows()) {
            sb.append(row.date()).append(";")
                    .append(row.referenceCode()).append(";")
                    .append(row.category()).append(";")
                    .append(row.status()).append(";")
                    .append(row.quantity()).append(";")
                    .append(row.primaryAmount()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateJsonBytes(AnalyticsDtos.ReportResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"reportType\":\"").append(report.reportType()).append("\",");
        sb.append("\"startDate\":\"").append(report.startDate()).append("\",");
        sb.append("\"endDate\":\"").append(report.endDate()).append("\",");
        sb.append("\"totalRows\":").append(report.totalElements()).append(",");
        sb.append("\"rows\":[");
        for (int i = 0; i < report.rows().size(); i++) {
            AnalyticsDtos.ReportDataRow r = report.rows().get(i);
            sb.append("{\"id\":\"").append(r.id()).append("\",");
            sb.append("\"date\":\"").append(r.date()).append("\",");
            sb.append("\"referenceCode\":\"").append(r.referenceCode()).append("\",");
            sb.append("\"description\":\"").append(r.description()).append("\",");
            sb.append("\"status\":\"").append(r.status()).append("\",");
            sb.append("\"amount\":").append(r.primaryAmount()).append("}");
            if (i < report.rows().size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
