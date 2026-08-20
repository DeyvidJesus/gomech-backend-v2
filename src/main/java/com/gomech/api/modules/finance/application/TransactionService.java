package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.TransactionDtos;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceAccount;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceCategory;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceAccountRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceCategoryRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final FinanceTransactionRepository transactionRepository;
    private final FinanceAccountRepository accountRepository;
    private final FinanceCategoryRepository categoryRepository;
    private final FinanceAccountService accountService;

    @Transactional(readOnly = true)
    public Page<TransactionDtos.Response> listTransactions(UUID tenantId, UUID unitId, UUID accountId, Pageable pageable) {
        Page<FinanceTransaction> page;
        if (accountId != null) {
            page = transactionRepository.findAllByTenantIdAndAccountId(tenantId, accountId, pageable);
        } else if (unitId != null) {
            page = transactionRepository.findAllByTenantIdAndUnitId(tenantId, unitId, pageable);
        } else {
            page = transactionRepository.findAllByTenantId(tenantId, pageable);
        }

        return page.map(this::mapToResponse);
    }

    @Transactional
    public TransactionDtos.Response createManualTransaction(TransactionDtos.Create request, UUID tenantId, UUID userId) {
        LocalDate compDate = request.competenceDate() != null ? request.competenceDate() : request.transactionDate();

        FinanceTransaction tx = FinanceTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(request.unitId())
                .accountId(request.accountId())
                .categoryId(request.categoryId())
                .type(request.type())
                .amount(request.amount())
                .transactionDate(request.transactionDate())
                .competenceDate(compDate)
                .description(request.description().trim())
                .notes(request.notes())
                .createdByUserId(userId)
                .build();

        tx = transactionRepository.save(tx);

        if (request.type() == TransactionType.CREDIT) {
            accountService.creditBalance(request.accountId(), request.amount(), tenantId);
        } else {
            accountService.debitBalance(request.accountId(), request.amount(), tenantId);
        }

        log.info("Created manual financial transaction {} ({}) amount R$ {}", tx.getId(), tx.getType(), tx.getAmount());
        return mapToResponse(tx);
    }

    private TransactionDtos.Response mapToResponse(FinanceTransaction t) {
        String accName = accountRepository.findById(t.getAccountId()).map(FinanceAccount::getName).orElse(null);
        String catName = null;
        if (t.getCategoryId() != null) {
            catName = categoryRepository.findById(t.getCategoryId()).map(FinanceCategory::getName).orElse(null);
        }

        return TransactionDtos.Response.builder()
                .id(t.getId())
                .tenantId(t.getTenantId())
                .unitId(t.getUnitId())
                .accountId(t.getAccountId())
                .accountName(accName)
                .categoryId(t.getCategoryId())
                .categoryName(catName)
                .receivableId(t.getReceivableId())
                .payableId(t.getPayableId())
                .type(t.getType())
                .amount(t.getAmount())
                .transactionDate(t.getTransactionDate())
                .competenceDate(t.getCompetenceDate())
                .description(t.getDescription())
                .status(t.getStatus())
                .sourceCorrelationId(t.getSourceCorrelationId())
                .notes(t.getNotes())
                .createdAt(t.getCreatedAt())
                .createdByUserId(t.getCreatedByUserId())
                .build();
    }
}
