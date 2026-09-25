package com.gomech.api.modules.analytics.application;

import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.domain.KpiCategory;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsDailyKpiSnapshot;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsFinancialProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsInventoryProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsToolProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.entity.AnalyticsWorkOrderProjection;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsDailyKpiSnapshotRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsFinancialProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsInventoryProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsToolProjectionRepository;
import com.gomech.api.modules.analytics.infrastructure.persistence.repository.AnalyticsWorkOrderProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsDashboardService {

    private final AnalyticsDailyKpiSnapshotRepository dailySnapshotRepository;
    private final AnalyticsWorkOrderProjectionRepository workOrderProjectionRepository;
    private final AnalyticsFinancialProjectionRepository financialProjectionRepository;
    private final AnalyticsInventoryProjectionRepository inventoryProjectionRepository;
    private final AnalyticsToolProjectionRepository toolProjectionRepository;

    @Transactional(readOnly = true)
    public AnalyticsDtos.DashboardSummaryResponse getDashboardSummary(UUID tenantId, UUID unitId, LocalDate startDate, LocalDate endDate) {
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        LocalDate start = (startDate != null) ? startDate : end.minusDays(29);

        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);

        OffsetDateTime startDt = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDt = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime prevStartDt = prevStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime prevEndDt = prevEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        // 1. Projeções de Ordens de Serviço
        List<AnalyticsWorkOrderProjection> workOrders = (unitId != null)
                ? workOrderProjectionRepository.findAllByTenantIdAndUnitIdAndOpenedAtBetween(tenantId, unitId, startDt, endDt)
                : workOrderProjectionRepository.findAllByTenantIdAndOpenedAtBetween(tenantId, startDt, endDt);

        List<AnalyticsWorkOrderProjection> prevWorkOrders = (unitId != null)
                ? workOrderProjectionRepository.findAllByTenantIdAndUnitIdAndOpenedAtBetween(tenantId, unitId, prevStartDt, prevEndDt)
                : workOrderProjectionRepository.findAllByTenantIdAndOpenedAtBetween(tenantId, prevStartDt, prevEndDt);

        List<AnalyticsWorkOrderProjection> completedWo = workOrders.stream()
                .filter(w -> "COMPLETED".equalsIgnoreCase(w.getStatus()))
                .toList();

        List<AnalyticsWorkOrderProjection> prevCompletedWo = prevWorkOrders.stream()
                .filter(w -> "COMPLETED".equalsIgnoreCase(w.getStatus()))
                .toList();

        BigDecimal totalRevenue = completedWo.stream()
                .map(AnalyticsWorkOrderProjection::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal prevTotalRevenue = prevCompletedWo.stream()
                .map(AnalyticsWorkOrderProjection::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal partsRevenue = completedWo.stream()
                .map(AnalyticsWorkOrderProjection::getPartsAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal servicesRevenue = completedWo.stream()
                .map(AnalyticsWorkOrderProjection::getServicesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long completedCount = completedWo.size();
        long prevCompletedCount = prevCompletedWo.size();

        BigDecimal avgTicket = (completedCount > 0)
                ? totalRevenue.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal prevAvgTicket = (prevCompletedCount > 0)
                ? prevTotalRevenue.divide(BigDecimal.valueOf(prevCompletedCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double avgTurnaround = completedWo.stream()
                .filter(w -> w.getTurnaroundHours() != null)
                .mapToDouble(w -> w.getTurnaroundHours().doubleValue())
                .average()
                .orElse(0.0);

        // 2. Snapshot metrics para Orçamentos e Agendamentos
        List<AnalyticsDailyKpiSnapshot> snapshots = (unitId != null)
                ? dailySnapshotRepository.findAllByTenantIdAndUnitIdAndDateBetween(tenantId, unitId, start, end)
                : dailySnapshotRepository.findAllByTenantIdAndDateBetween(tenantId, start, end);

        long totalQuotes = getSnapshotCount(snapshots, "QUOTE_CREATED_COUNT");
        long approvedQuotes = getSnapshotCount(snapshots, "QUOTE_APPROVED_COUNT");
        double quoteConversion = (totalQuotes > 0) ? ((double) approvedQuotes / totalQuotes) * 100.0 : 0.0;

        long scheduledAppointments = getSnapshotCount(snapshots, "APPOINTMENT_SCHEDULED_COUNT");
        long completedInspections = getSnapshotCount(snapshots, "INSPECTION_COMPLETED_COUNT");

        // 3. Projeções Financeiras
        List<AnalyticsFinancialProjection> finRecords = (unitId != null)
                ? financialProjectionRepository.findAllByTenantIdAndUnitIdAndOccurredAtBetween(tenantId, unitId, startDt, endDt)
                : financialProjectionRepository.findAllByTenantIdAndOccurredAtBetween(tenantId, startDt, endDt);

        BigDecimal recTotal = sumFinancial(finRecords, "RECEIVABLE", null);
        BigDecimal recPaid = sumFinancial(finRecords, "RECEIVABLE", "PAID");
        BigDecimal recPending = sumFinancial(finRecords, "RECEIVABLE", "PENDING");
        BigDecimal recOverdue = sumFinancial(finRecords, "RECEIVABLE", "OVERDUE");

        BigDecimal payTotal = sumFinancial(finRecords, "PAYABLE", null);
        BigDecimal payPaid = sumFinancial(finRecords, "PAYABLE", "PAID");
        BigDecimal payPending = sumFinancial(finRecords, "PAYABLE", "PENDING");

        BigDecimal netProfit = recPaid.subtract(payPaid);
        double operatingMargin = (recPaid.compareTo(BigDecimal.ZERO) > 0)
                ? netProfit.divide(recPaid, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        double delinquencyRate = (recTotal.compareTo(BigDecimal.ZERO) > 0)
                ? recOverdue.divide(recTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        // 4. Projeções de Estoque
        List<AnalyticsInventoryProjection> invRecords = (unitId != null)
                ? inventoryProjectionRepository.findAllByTenantIdAndUnitIdAndOccurredAtBetween(tenantId, unitId, startDt, endDt)
                : inventoryProjectionRepository.findAllByTenantIdAndOccurredAtBetween(tenantId, startDt, endDt);

        BigDecimal purchaseSpend = invRecords.stream()
                .filter(i -> "PURCHASE".equalsIgnoreCase(i.getMovementType()))
                .map(AnalyticsInventoryProjection::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal stockConsumed = invRecords.stream()
                .filter(i -> "WORK_ORDER_CONSUMPTION".equalsIgnoreCase(i.getMovementType()))
                .map(AnalyticsInventoryProjection::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AnalyticsDtos.BreakdownItemDto> topParts = invRecords.stream()
                .filter(i -> "WORK_ORDER_CONSUMPTION".equalsIgnoreCase(i.getMovementType()) && i.getProductName() != null)
                .collect(Collectors.groupingBy(AnalyticsInventoryProjection::getProductName))
                .entrySet().stream()
                .map(e -> {
                    BigDecimal val = e.getValue().stream()
                            .map(AnalyticsInventoryProjection::getTotalValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return AnalyticsDtos.BreakdownItemDto.builder()
                            .key(e.getKey())
                            .label(e.getKey())
                            .count((long) e.getValue().size())
                            .value(val)
                            .percentage(stockConsumed.compareTo(BigDecimal.ZERO) > 0
                                    ? val.divide(stockConsumed, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                                    : 0.0)
                            .colorHex("#2563eb")
                            .build();
                })
                .sorted(Comparator.comparing(AnalyticsDtos.BreakdownItemDto::value).reversed())
                .limit(5)
                .toList();

        // 5. Projeções de Ferramentas
        List<AnalyticsToolProjection> toolRecords = (unitId != null)
                ? toolProjectionRepository.findAllByTenantIdAndUnitIdAndOccurredAtBetween(tenantId, unitId, startDt, endDt)
                : toolProjectionRepository.findAllByTenantIdAndOccurredAtBetween(tenantId, startDt, endDt);

        long activeCustodies = getSnapshotCount(snapshots, "TOOL_ACTIVE_CUSTODY");
        BigDecimal maintenanceCost = toolRecords.stream()
                .filter(t -> "MAINTENANCE_COMPLETED".equalsIgnoreCase(t.getEventType()))
                .map(AnalyticsToolProjection::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal downtimeHours = toolRecords.stream()
                .filter(t -> "MAINTENANCE_COMPLETED".equalsIgnoreCase(t.getEventType()))
                .map(AnalyticsToolProjection::getDowntimeHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 6. Montagem dos Hero KPI Cards com cálculo de variação %
        List<AnalyticsDtos.KpiHeroCardDto> heroKpis = List.of(
                buildHeroCard(
                        "WO_TOTAL_REVENUE",
                        "Faturamento Total",
                        totalRevenue,
                        "R$ " + formatMoney(totalRevenue),
                        "R$",
                        prevTotalRevenue,
                        KpiCategory.OPERATIONS,
                        "Receita gerada por ordens de serviço concluídas"
                ),
                buildHeroCard(
                        "WO_COMPLETED_COUNT",
                        "OS Concluídas",
                        BigDecimal.valueOf(completedCount),
                        String.valueOf(completedCount),
                        "OS",
                        BigDecimal.valueOf(prevCompletedCount),
                        KpiCategory.OPERATIONS,
                        "Volume de manutenções finalizadas no período"
                ),
                buildHeroCard(
                        "WO_AVG_TICKET",
                        "Ticket Médio",
                        avgTicket,
                        "R$ " + formatMoney(avgTicket),
                        "R$",
                        prevAvgTicket,
                        KpiCategory.OPERATIONS,
                        "Valor médio por ordem de serviço"
                ),
                buildHeroCard(
                        "FIN_NET_PROFIT",
                        "Lucro Líquido",
                        netProfit,
                        "R$ " + formatMoney(netProfit),
                        "R$",
                        BigDecimal.ZERO,
                        KpiCategory.FINANCE,
                        "Recebimentos liquidados menos despesas pagas"
                ),
                buildHeroCard(
                        "QUOTE_CONVERSION_RATE",
                        "Conversão de Orçamentos",
                        BigDecimal.valueOf(quoteConversion).setScale(1, RoundingMode.HALF_UP),
                        String.format("%.1f%%", quoteConversion),
                        "%",
                        BigDecimal.valueOf(50.0),
                        KpiCategory.OPERATIONS,
                        "Percentual de orçamentos aprovados"
                )
        );

        // 7. TimeSeries diária para gráficos
        List<AnalyticsDtos.TimeSeriesPointDto> timeSeries = buildTimeSeries(start, end, workOrders, finRecords);

        // 8. Breakdown Peças vs Mão de Obra
        BigDecimal totalPartsPlusServices = partsRevenue.add(servicesRevenue);
        double partsPct = (totalPartsPlusServices.compareTo(BigDecimal.ZERO) > 0)
                ? partsRevenue.divide(totalPartsPlusServices, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 50.0;
        double servicesPct = (totalPartsPlusServices.compareTo(BigDecimal.ZERO) > 0)
                ? servicesRevenue.divide(totalPartsPlusServices, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 50.0;

        List<AnalyticsDtos.BreakdownItemDto> partsVsServices = List.of(
                AnalyticsDtos.BreakdownItemDto.builder()
                        .key("SERVICES")
                        .label("Mão de Obra & Serviços")
                        .value(servicesRevenue)
                        .percentage(servicesPct)
                        .colorHex("#3b82f6")
                        .build(),
                AnalyticsDtos.BreakdownItemDto.builder()
                        .key("PARTS")
                        .label("Peças & Componentes")
                        .value(partsRevenue)
                        .percentage(partsPct)
                        .colorHex("#10b981")
                        .build()
        );

        // 9. Top Mecânicos
        List<AnalyticsDtos.TechnicianPerformanceDto> topTechnicians = buildTechnicianRanking(completedWo);

        return AnalyticsDtos.DashboardSummaryResponse.builder()
                .tenantId(tenantId)
                .unitId(unitId)
                .startDate(start)
                .endDate(end)
                .heroKpis(heroKpis)
                .operational(AnalyticsDtos.OperationalSummary.builder()
                        .totalWorkOrders((long) workOrders.size())
                        .completedWorkOrders(completedCount)
                        .inProgressWorkOrders(workOrders.stream().filter(w -> !"COMPLETED".equalsIgnoreCase(w.getStatus()) && !"CANCELED".equalsIgnoreCase(w.getStatus())).count())
                        .canceledWorkOrders(workOrders.stream().filter(w -> "CANCELED".equalsIgnoreCase(w.getStatus())).count())
                        .totalRevenue(totalRevenue)
                        .avgTicket(avgTicket)
                        .avgTurnaroundHours(BigDecimal.valueOf(avgTurnaround).setScale(1, RoundingMode.HALF_UP))
                        .totalQuotes(totalQuotes)
                        .approvedQuotes(approvedQuotes)
                        .quoteConversionRate(quoteConversion)
                        .scheduledAppointments(scheduledAppointments)
                        .completedInspections(completedInspections)
                        .build())
                .financial(AnalyticsDtos.FinancialSummary.builder()
                        .grossRevenue(recTotal)
                        .netRevenue(recPaid)
                        .receivablesTotal(recTotal)
                        .receivablesPaid(recPaid)
                        .receivablesPending(recPending)
                        .receivablesOverdue(recOverdue)
                        .payablesTotal(payTotal)
                        .payablesPaid(payPaid)
                        .payablesPending(payPending)
                        .netProfit(netProfit)
                        .operatingMargin(operatingMargin)
                        .delinquencyRate(delinquencyRate)
                        .build())
                .inventory(AnalyticsDtos.InventorySummary.builder()
                        .totalPurchaseSpend(purchaseSpend)
                        .totalStockConsumed(stockConsumed)
                        .totalMovementsCount((long) invRecords.size())
                        .lowStockItemsCount(0L)
                        .topConsumedParts(topParts)
                        .build())
                .tools(AnalyticsDtos.ToolsSummary.builder()
                        .totalToolsCount(25L)
                        .activeCustodiesCount(activeCustodies)
                        .toolsInMaintenanceCount((long) toolRecords.size())
                        .totalMaintenanceCost(maintenanceCost)
                        .totalDowntimeHours(downtimeHours)
                        .utilizationRate(85.0)
                        .build())
                .billing(AnalyticsDtos.BillingSummary.builder()
                        .subscriptionStatus("ACTIVE")
                        .planCode("PRO")
                        .reportsQuotaUsed(12L)
                        .reportsQuotaLimit(500L)
                        .aiQuotaUsed(85L)
                        .aiQuotaLimit(5000L)
                        .build())
                .revenueTimeSeries(timeSeries)
                .serviceVsPartsBreakdown(partsVsServices)
                .topTechnicians(topTechnicians)
                .refreshedAt(OffsetDateTime.now())
                .build();
    }

    private AnalyticsDtos.KpiHeroCardDto buildHeroCard(
            String code,
            String title,
            BigDecimal value,
            String formattedValue,
            String unit,
            BigDecimal previousValue,
            KpiCategory category,
            String helpText
    ) {
        Double percentChange = 0.0;
        String trend = "NEUTRAL";

        if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = value.subtract(previousValue);
            percentChange = diff.divide(previousValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
            if (percentChange > 0.1) trend = "UP";
            else if (percentChange < -0.1) trend = "DOWN";
        } else if (value.compareTo(BigDecimal.ZERO) > 0) {
            percentChange = 100.0;
            trend = "UP";
        }

        return AnalyticsDtos.KpiHeroCardDto.builder()
                .code(code)
                .title(title)
                .value(value)
                .formattedValue(formattedValue)
                .unit(unit)
                .previousPeriodValue(previousValue != null ? previousValue : BigDecimal.ZERO)
                .percentChange(percentChange)
                .trendDirection(trend)
                .category(category)
                .helpText(helpText)
                .build();
    }

    private List<AnalyticsDtos.TimeSeriesPointDto> buildTimeSeries(
            LocalDate start,
            LocalDate end,
            List<AnalyticsWorkOrderProjection> workOrders,
            List<AnalyticsFinancialProjection> finRecords
    ) {
        List<AnalyticsDtos.TimeSeriesPointDto> points = new ArrayList<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM");

        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            LocalDate d = cur;
            List<AnalyticsWorkOrderProjection> dayWo = workOrders.stream()
                    .filter(w -> w.getCompletedAt() != null && w.getCompletedAt().toLocalDate().equals(d))
                    .toList();

            BigDecimal dayRev = dayWo.stream().map(AnalyticsWorkOrderProjection::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal dayParts = dayWo.stream().map(AnalyticsWorkOrderProjection::getPartsAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal dayServices = dayWo.stream().map(AnalyticsWorkOrderProjection::getServicesAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal dayExp = finRecords.stream()
                    .filter(f -> f.getPaidDate() != null && f.getPaidDate().equals(d) && "PAYABLE".equalsIgnoreCase(f.getRecordType()))
                    .map(AnalyticsFinancialProjection::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            points.add(AnalyticsDtos.TimeSeriesPointDto.builder()
                    .date(d)
                    .label(d.format(dtf))
                    .revenue(dayRev)
                    .partsRevenue(dayParts)
                    .servicesRevenue(dayServices)
                    .workOrdersCount((long) dayWo.size())
                    .quotesCount(0L)
                    .expenses(dayExp)
                    .netProfit(dayRev.subtract(dayExp))
                    .build());

            cur = cur.plusDays(1);
        }

        return points;
    }

    private List<AnalyticsDtos.TechnicianPerformanceDto> buildTechnicianRanking(List<AnalyticsWorkOrderProjection> completedWo) {
        return completedWo.stream()
                .filter(w -> w.getMechanicUserId() != null)
                .collect(Collectors.groupingBy(AnalyticsWorkOrderProjection::getMechanicUserId))
                .entrySet().stream()
                .map(e -> {
                    BigDecimal total = e.getValue().stream().map(AnalyticsWorkOrderProjection::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    double avgTurn = e.getValue().stream().filter(w -> w.getTurnaroundHours() != null).mapToDouble(w -> w.getTurnaroundHours().doubleValue()).average().orElse(2.0);
                    return AnalyticsDtos.TechnicianPerformanceDto.builder()
                            .mechanicUserId(e.getKey())
                            .mechanicName("Mecânico " + e.getKey().toString().substring(0, 6).toUpperCase())
                            .completedOrdersCount((long) e.getValue().size())
                            .totalRevenue(total)
                            .avgTurnaroundHours(BigDecimal.valueOf(avgTurn).setScale(1, RoundingMode.HALF_UP))
                            .build();
                })
                .sorted(Comparator.comparing(AnalyticsDtos.TechnicianPerformanceDto::totalRevenue).reversed())
                .limit(5)
                .toList();
    }

    private long getSnapshotCount(List<AnalyticsDailyKpiSnapshot> snapshots, String metricName) {
        return snapshots.stream()
                .filter(s -> metricName.equalsIgnoreCase(s.getMetricName()))
                .mapToLong(AnalyticsDailyKpiSnapshot::getMetricCount)
                .sum();
    }

    private BigDecimal sumFinancial(List<AnalyticsFinancialProjection> records, String recordType, String status) {
        return records.stream()
                .filter(r -> recordType.equalsIgnoreCase(r.getRecordType()))
                .filter(r -> status == null || status.equalsIgnoreCase(r.getStatus()))
                .map(AnalyticsFinancialProjection::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0,00";
        return String.format("%,.2f", amount.doubleValue());
    }
}
