package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.DreReportDtos;
import com.gomech.api.modules.finance.domain.DreCategoryType;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceCategory;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceCategoryRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DreReportServiceTest {

    @Mock
    private FinanceTransactionRepository transactionRepository;

    @Mock
    private FinanceCategoryRepository categoryRepository;

    @InjectMocks
    private DreReportService dreReportService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should generate DRE report with Gross Profit and Net Profit margins")
    void shouldGenerateDreReportCorrectly() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);

        UUID catRevenueId = UUID.randomUUID();
        UUID catCostId = UUID.randomUUID();
        UUID catExpenseId = UUID.randomUUID();

        FinanceCategory catRevenue = FinanceCategory.builder()
                .id(catRevenueId)
                .tenantId(tenantId)
                .name("Serviços de Mecânica")
                .type(TransactionType.CREDIT)
                .dreCategoryType(DreCategoryType.GROSS_REVENUE)
                .build();

        FinanceCategory catCost = FinanceCategory.builder()
                .id(catCostId)
                .tenantId(tenantId)
                .name("Peças e Insumos (CMV)")
                .type(TransactionType.DEBIT)
                .dreCategoryType(DreCategoryType.VARIABLE_COST)
                .build();

        FinanceCategory catExpense = FinanceCategory.builder()
                .id(catExpenseId)
                .tenantId(tenantId)
                .name("Aluguel da Oficina")
                .type(TransactionType.DEBIT)
                .dreCategoryType(DreCategoryType.OPERATING_EXPENSE)
                .build();

        FinanceTransaction txRevenue = FinanceTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .categoryId(catRevenueId)
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(20000.00))
                .competenceDate(LocalDate.of(2026, 8, 10))
                .build();

        FinanceTransaction txCost = FinanceTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .categoryId(catCostId)
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(6000.00))
                .competenceDate(LocalDate.of(2026, 8, 12))
                .build();

        FinanceTransaction txExpense = FinanceTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .categoryId(catExpenseId)
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(4000.00))
                .competenceDate(LocalDate.of(2026, 8, 20))
                .build();

        when(categoryRepository.findAllByTenantId(tenantId)).thenReturn(List.of(catRevenue, catCost, catExpense));
        when(transactionRepository.findAllByTenantIdAndCompetenceDateBetween(tenantId, start, end))
                .thenReturn(List.of(txRevenue, txCost, txExpense));

        DreReportDtos.DreReport dre = dreReportService.generateDre(tenantId, start, end);

        assertThat(dre).isNotNull();
        assertThat(dre.grossRevenue()).isEqualByComparingTo("20000.00");
        assertThat(dre.variableCosts()).isEqualByComparingTo("6000.00");
        assertThat(dre.grossProfit()).isEqualByComparingTo("14000.00");
        assertThat(dre.operatingExpenses()).isEqualByComparingTo("4000.00");
        assertThat(dre.netProfit()).isEqualByComparingTo("10000.00");
        assertThat(dre.grossMarginPercentage()).isEqualTo(70.0);
        assertThat(dre.netMarginPercentage()).isEqualTo(50.0);
    }
}
