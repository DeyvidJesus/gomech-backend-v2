package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.CashFlowDtos;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceAccount;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceAccountRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashFlowService {

    private final FinanceTransactionRepository transactionRepository;
    private final FinanceAccountRepository accountRepository;

    @Transactional(readOnly = true)
    public CashFlowDtos.CashFlowReport generateCashFlow(UUID tenantId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        List<FinanceAccount> accounts = accountRepository.findAllByTenantId(tenantId);
        BigDecimal currentTotalBalance = accounts.stream()
                .map(FinanceAccount::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FinanceTransaction> transactions = transactionRepository.findAllByTenantIdAndTransactionDateBetween(
                tenantId, startDate, endDate);

        // Group transactions by date
        Map<LocalDate, List<FinanceTransaction>> txByDate = new TreeMap<>();
        LocalDate cur = startDate;
        while (!cur.isAfter(endDate)) {
            txByDate.put(cur, new ArrayList<>());
            cur = cur.plusDays(1);
        }

        for (FinanceTransaction tx : transactions) {
            if (txByDate.containsKey(tx.getTransactionDate())) {
                txByDate.get(tx.getTransactionDate()).add(tx);
            }
        }

        BigDecimal totalInflows = BigDecimal.ZERO;
        BigDecimal totalOutflows = BigDecimal.ZERO;
        List<CashFlowDtos.CashFlowEntry> dailyEntries = new ArrayList<>();
        BigDecimal runningBalance = currentTotalBalance; // Or accumulated from initial

        for (Map.Entry<LocalDate, List<FinanceTransaction>> entry : txByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<FinanceTransaction> dayTxs = entry.getValue();

            BigDecimal dayInflows = dayTxs.stream()
                    .filter(t -> t.getType() == TransactionType.CREDIT)
                    .map(FinanceTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal dayOutflows = dayTxs.stream()
                    .filter(t -> t.getType() == TransactionType.DEBIT)
                    .map(FinanceTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal dayNet = dayInflows.subtract(dayOutflows);
            totalInflows = totalInflows.add(dayInflows);
            totalOutflows = totalOutflows.add(dayOutflows);

            dailyEntries.add(CashFlowDtos.CashFlowEntry.builder()
                    .date(date)
                    .inflows(dayInflows)
                    .outflows(dayOutflows)
                    .netAmount(dayNet)
                    .accumulatedBalance(runningBalance.add(dayNet))
                    .build());
        }

        BigDecimal netCashFlow = totalInflows.subtract(totalOutflows);

        return CashFlowDtos.CashFlowReport.builder()
                .startDate(startDate)
                .endDate(endDate)
                .initialBalance(currentTotalBalance.subtract(netCashFlow))
                .totalInflows(totalInflows)
                .totalOutflows(totalOutflows)
                .netCashFlow(netCashFlow)
                .finalBalance(currentTotalBalance)
                .dailyEntries(dailyEntries)
                .build();
    }
}
