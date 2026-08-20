package com.gomech.api.modules.finance.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class DreReportDtos {

    @Builder
    public record DreLineItem(
            String categoryName,
            BigDecimal amount,
            Double verticalPercentage // % em relação à Receita Bruta
    ) {}

    @Builder
    public record DreGroup(
            String groupName,
            BigDecimal totalAmount,
            Double verticalPercentage,
            List<DreLineItem> items
    ) {}

    @Builder
    public record DreReport(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal grossRevenue,          // Receita Operacional Bruta
            BigDecimal deductionsAndTaxes,     // Deduções e Impostos
            BigDecimal netRevenue,             // Receita Líquida (Bruta - Deduções)
            BigDecimal variableCosts,          // Custos das Peças/Serviços (CMV)
            BigDecimal grossProfit,            // Lucro Bruto (Receita Líquida - Custos Variáveis)
            Double grossMarginPercentage,      // Margem Bruta %
            BigDecimal operatingExpenses,      // Despesas Operacionais (Fixas, Administrativas)
            BigDecimal operatingProfit,        // Lucro Operacional (EBITDA / LAJIDA)
            BigDecimal financialResult,        // Resultado Financeiro (Juros/Rendimentos)
            BigDecimal netProfit,              // Lucro / Prejuízo Líquido do Exercício
            Double netMarginPercentage,        // Margem Líquida %
            List<DreGroup> groups
    ) {}
}
