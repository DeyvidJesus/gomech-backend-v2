package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.CashFlowDtos;
import com.gomech.api.modules.finance.domain.AccountType;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceAccount;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceAccountRepository;
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
class CashFlowServiceTest {

    @Mock
    private FinanceTransactionRepository transactionRepository;

    @Mock
    private FinanceAccountRepository accountRepository;

    @InjectMocks
    private CashFlowService cashFlowService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should generate cash flow report with correct inflows, outflows, and net cash flow")
    void shouldGenerateCashFlowCorrectly() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);

        FinanceAccount account = FinanceAccount.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Banco do Brasil")
                .type(AccountType.BANK_ACCOUNT)
                .currentBalance(BigDecimal.valueOf(10000.00))
                .build();

        FinanceTransaction tx1 = FinanceTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(3000.00))
                .transactionDate(LocalDate.of(2026, 8, 10))
                .build();

        FinanceTransaction tx2 = FinanceTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(1000.00))
                .transactionDate(LocalDate.of(2026, 8, 15))
                .build();

        when(accountRepository.findAllByTenantId(tenantId)).thenReturn(List.of(account));
        when(transactionRepository.findAllByTenantIdAndTransactionDateBetween(tenantId, start, end))
                .thenReturn(List.of(tx1, tx2));

        CashFlowDtos.CashFlowReport report = cashFlowService.generateCashFlow(tenantId, start, end);

        assertThat(report).isNotNull();
        assertThat(report.totalInflows()).isEqualByComparingTo("3000.00");
        assertThat(report.totalOutflows()).isEqualByComparingTo("1000.00");
        assertThat(report.netCashFlow()).isEqualByComparingTo("2000.00");
        assertThat(report.finalBalance()).isEqualByComparingTo("10000.00");
        assertThat(report.dailyEntries()).isNotEmpty();
    }
}
