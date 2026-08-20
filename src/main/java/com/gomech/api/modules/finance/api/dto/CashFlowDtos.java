package com.gomech.api.modules.finance.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CashFlowDtos {

    @Builder
    public record CashFlowEntry(
            LocalDate date,
            BigDecimal inflows,   // Recebimentos (Entradas)
            BigDecimal outflows,  // Pagamentos (Saídas)
            BigDecimal netAmount, // Saldo do Dia (Inflows - Outflows)
            BigDecimal accumulatedBalance // Saldo Acumulado
    ) {}

    @Builder
    public record CashFlowReport(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal initialBalance,
            BigDecimal totalInflows,
            BigDecimal totalOutflows,
            BigDecimal netCashFlow,
            BigDecimal finalBalance,
            List<CashFlowEntry> dailyEntries
    ) {}
}
