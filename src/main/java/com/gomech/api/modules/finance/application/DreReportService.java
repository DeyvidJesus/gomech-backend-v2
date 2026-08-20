package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.DreReportDtos;
import com.gomech.api.modules.finance.domain.DreCategoryType;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceCategory;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceCategoryRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DreReportService {

    private final FinanceTransactionRepository transactionRepository;
    private final FinanceCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public DreReportDtos.DreReport generateDre(UUID tenantId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        List<FinanceTransaction> transactions = transactionRepository.findAllByTenantIdAndCompetenceDateBetween(
                tenantId, startDate, endDate);

        Map<UUID, FinanceCategory> categoryMap = new HashMap<>();
        for (FinanceCategory cat : categoryRepository.findAllByTenantId(tenantId)) {
            categoryMap.put(cat.getId(), cat);
        }

        // Group transactions by DreCategoryType
        Map<DreCategoryType, Map<String, BigDecimal>> breakdown = new EnumMap<>(DreCategoryType.class);
        for (DreCategoryType type : DreCategoryType.values()) {
            breakdown.put(type, new LinkedHashMap<>());
        }

        for (FinanceTransaction tx : transactions) {
            FinanceCategory cat = tx.getCategoryId() != null ? categoryMap.get(tx.getCategoryId()) : null;
            DreCategoryType dreType;
            String catName;

            if (cat != null) {
                dreType = cat.getDreCategoryType();
                catName = cat.getName();
            } else if (tx.getType() == TransactionType.CREDIT) {
                dreType = DreCategoryType.GROSS_REVENUE;
                catName = "Receitas Gerais (Sem Categoria)";
            } else {
                dreType = DreCategoryType.OPERATING_EXPENSE;
                catName = "Despesas Gerais (Sem Categoria)";
            }

            Map<String, BigDecimal> catTotals = breakdown.get(dreType);
            catTotals.put(catName, catTotals.getOrDefault(catName, BigDecimal.ZERO).add(tx.getAmount()));
        }

        BigDecimal grossRevenue = sumMap(breakdown.get(DreCategoryType.GROSS_REVENUE));
        BigDecimal deductions = sumMap(breakdown.get(DreCategoryType.TAXES_AND_DEDUCTIONS));
        BigDecimal netRevenue = grossRevenue.subtract(deductions);
        BigDecimal variableCosts = sumMap(breakdown.get(DreCategoryType.VARIABLE_COST));
        BigDecimal grossProfit = netRevenue.subtract(variableCosts);
        BigDecimal operatingExpenses = sumMap(breakdown.get(DreCategoryType.OPERATING_EXPENSE));
        BigDecimal operatingProfit = grossProfit.subtract(operatingExpenses);
        BigDecimal financialResult = sumMap(breakdown.get(DreCategoryType.FINANCIAL_RESULT));
        BigDecimal netProfit = operatingProfit.add(financialResult);

        Double grossMargin = calcPercentage(grossProfit, grossRevenue);
        Double netMargin = calcPercentage(netProfit, grossRevenue);

        List<DreReportDtos.DreGroup> groups = new ArrayList<>();
        groups.add(buildGroup("1. Receita Operacional Bruta", grossRevenue, breakdown.get(DreCategoryType.GROSS_REVENUE), grossRevenue));
        groups.add(buildGroup("2. Deduções e Impostos", deductions, breakdown.get(DreCategoryType.TAXES_AND_DEDUCTIONS), grossRevenue));
        groups.add(buildGroup("3. Custos Variáveis (CMV)", variableCosts, breakdown.get(DreCategoryType.VARIABLE_COST), grossRevenue));
        groups.add(buildGroup("4. Despesas Operacionais", operatingExpenses, breakdown.get(DreCategoryType.OPERATING_EXPENSE), grossRevenue));

        return DreReportDtos.DreReport.builder()
                .startDate(startDate)
                .endDate(endDate)
                .grossRevenue(grossRevenue)
                .deductionsAndTaxes(deductions)
                .netRevenue(netRevenue)
                .variableCosts(variableCosts)
                .grossProfit(grossProfit)
                .grossMarginPercentage(grossMargin)
                .operatingExpenses(operatingExpenses)
                .operatingProfit(operatingProfit)
                .financialResult(financialResult)
                .netProfit(netProfit)
                .netMarginPercentage(netMargin)
                .groups(groups)
                .build();
    }

    private BigDecimal sumMap(Map<String, BigDecimal> map) {
        if (map == null) return BigDecimal.ZERO;
        return map.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Double calcPercentage(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.compareTo(BigDecimal.ZERO) == 0 || part == null) {
            return 0.0;
        }
        return part.divide(whole, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private DreReportDtos.DreGroup buildGroup(String name, BigDecimal total, Map<String, BigDecimal> itemsMap, BigDecimal baseGross) {
        List<DreReportDtos.DreLineItem> items = new ArrayList<>();
        if (itemsMap != null) {
            for (Map.Entry<String, BigDecimal> entry : itemsMap.entrySet()) {
                items.add(DreReportDtos.DreLineItem.builder()
                        .categoryName(entry.getKey())
                        .amount(entry.getValue())
                        .verticalPercentage(calcPercentage(entry.getValue(), baseGross))
                        .build());
            }
        }

        return DreReportDtos.DreGroup.builder()
                .groupName(name)
                .totalAmount(total)
                .verticalPercentage(calcPercentage(total, baseGross))
                .items(items)
                .build();
    }
}
