package com.gomech.api.modules.analytics.api.dto;

import com.gomech.api.modules.analytics.domain.AggregationInterval;
import com.gomech.api.modules.analytics.domain.ExportFormat;
import com.gomech.api.modules.analytics.domain.KpiCategory;
import com.gomech.api.modules.analytics.domain.ReportType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    @Builder
    public record DashboardSummaryResponse(
            UUID tenantId,
            UUID unitId,
            LocalDate startDate,
            LocalDate endDate,
            List<KpiHeroCardDto> heroKpis,
            OperationalSummary operational,
            FinancialSummary financial,
            InventorySummary inventory,
            ToolsSummary tools,
            BillingSummary billing,
            List<TimeSeriesPointDto> revenueTimeSeries,
            List<BreakdownItemDto> serviceVsPartsBreakdown,
            List<TechnicianPerformanceDto> topTechnicians,
            OffsetDateTime refreshedAt
    ) {}

    @Builder
    public record KpiHeroCardDto(
            String code,
            String title,
            BigDecimal value,
            String formattedValue,
            String unit,
            BigDecimal previousPeriodValue,
            Double percentChange,
            String trendDirection, // UP, DOWN, NEUTRAL
            KpiCategory category,
            String helpText
    ) {}

    @Builder
    public record TimeSeriesPointDto(
            LocalDate date,
            String label,
            BigDecimal revenue,
            BigDecimal partsRevenue,
            BigDecimal servicesRevenue,
            Long workOrdersCount,
            Long quotesCount,
            BigDecimal expenses,
            BigDecimal netProfit
    ) {}

    @Builder
    public record BreakdownItemDto(
            String key,
            String label,
            Long count,
            BigDecimal value,
            Double percentage,
            String colorHex
    ) {}

    @Builder
    public record TechnicianPerformanceDto(
            UUID mechanicUserId,
            String mechanicName,
            Long completedOrdersCount,
            BigDecimal totalRevenue,
            BigDecimal avgTurnaroundHours
    ) {}

    @Builder
    public record OperationalSummary(
            Long totalWorkOrders,
            Long completedWorkOrders,
            Long inProgressWorkOrders,
            Long canceledWorkOrders,
            BigDecimal totalRevenue,
            BigDecimal avgTicket,
            BigDecimal avgTurnaroundHours,
            Long totalQuotes,
            Long approvedQuotes,
            Double quoteConversionRate,
            Long scheduledAppointments,
            Long completedInspections
    ) {}

    @Builder
    public record FinancialSummary(
            BigDecimal grossRevenue,
            BigDecimal netRevenue,
            BigDecimal receivablesTotal,
            BigDecimal receivablesPaid,
            BigDecimal receivablesPending,
            BigDecimal receivablesOverdue,
            BigDecimal payablesTotal,
            BigDecimal payablesPaid,
            BigDecimal payablesPending,
            BigDecimal netProfit,
            Double operatingMargin,
            Double delinquencyRate
    ) {}

    @Builder
    public record InventorySummary(
            BigDecimal totalPurchaseSpend,
            BigDecimal totalStockConsumed,
            Long totalMovementsCount,
            Long lowStockItemsCount,
            List<BreakdownItemDto> topConsumedParts
    ) {}

    @Builder
    public record ToolsSummary(
            Long totalToolsCount,
            Long activeCustodiesCount,
            Long toolsInMaintenanceCount,
            BigDecimal totalMaintenanceCost,
            BigDecimal totalDowntimeHours,
            Double utilizationRate
    ) {}

    @Builder
    public record BillingSummary(
            String subscriptionStatus,
            String planCode,
            Long reportsQuotaUsed,
            Long reportsQuotaLimit,
            Long aiQuotaUsed,
            Long aiQuotaLimit
    ) {}

    @Builder
    public record ReportQueryRequest(
            ReportType reportType,
            LocalDate startDate,
            LocalDate endDate,
            UUID unitId,
            AggregationInterval interval,
            String search
    ) {}

    @Builder
    public record ReportResponse(
            ReportType reportType,
            UUID tenantId,
            UUID unitId,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, Object> summaryMetrics,
            List<String> columnHeaders,
            List<ReportDataRow> rows,
            long totalElements,
            int totalPages,
            int currentPage,
            OffsetDateTime generatedAt
    ) {}

    @Builder
    public record ReportDataRow(
            String id,
            LocalDate date,
            String dimension,
            String referenceCode,
            String category,
            String description,
            String status,
            BigDecimal quantity,
            BigDecimal primaryAmount,
            BigDecimal secondaryAmount,
            Map<String, Object> extraAttributes
    ) {}

    @Builder
    public record ExportRequest(
            ReportType reportType,
            ExportFormat format,
            LocalDate startDate,
            LocalDate endDate,
            UUID unitId,
            AggregationInterval interval
    ) {}

    @Builder
    public record KpiDefinitionDto(
            String code,
            String name,
            String description,
            KpiCategory category,
            String formula,
            String unit,
            List<String> dimensions,
            String refreshPolicy
    ) {}
}
