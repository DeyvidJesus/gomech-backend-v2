package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.PayableDtos;
import com.gomech.api.modules.finance.domain.InvalidFinanceOperationException;
import com.gomech.api.modules.finance.domain.PayableNotFoundException;
import com.gomech.api.modules.finance.domain.PayableStatus;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceAccount;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceCategory;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinancePayable;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceAccountRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceCategoryRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinancePayableRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayableService {

    private final FinancePayableRepository payableRepository;
    private final FinanceAccountRepository accountRepository;
    private final FinanceCategoryRepository categoryRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final FinanceAccountService accountService;

    @Transactional(readOnly = true)
    public Page<PayableDtos.Response> listPayables(UUID tenantId, UUID unitId, PayableStatus status, Pageable pageable) {
        Page<FinancePayable> page;
        if (unitId != null && status != null) {
            page = payableRepository.findAllByTenantIdAndUnitIdAndStatus(tenantId, unitId, status, pageable);
        } else if (unitId != null) {
            page = payableRepository.findAllByTenantIdAndUnitId(tenantId, unitId, pageable);
        } else if (status != null) {
            page = payableRepository.findAllByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            page = payableRepository.findAllByTenantId(tenantId, pageable);
        }

        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PayableDtos.Response getPayable(UUID id, UUID tenantId) {
        FinancePayable payable = payableRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new PayableNotFoundException(id));
        return mapToResponse(payable);
    }

    @Transactional
    public PayableDtos.Response createPayable(PayableDtos.Create request, UUID tenantId) {
        if (request.sourceCorrelationId() != null && !request.sourceCorrelationId().isBlank()) {
            Optional<FinancePayable> existing = payableRepository.findByTenantIdAndSourceCorrelationId(
                    tenantId, request.sourceCorrelationId().trim());
            if (existing.isPresent()) {
                log.warn("Payable already exists for correlation ID: {}", request.sourceCorrelationId());
                return mapToResponse(existing.get());
            }
        }

        FinancePayable payable = FinancePayable.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(request.unitId())
                .supplierName(request.supplierName().trim())
                .inventoryPurchaseId(request.inventoryPurchaseId())
                .description(request.description().trim())
                .amount(request.amount())
                .paidAmount(BigDecimal.ZERO)
                .dueDate(request.dueDate())
                .status(PayableStatus.PENDING)
                .categoryId(request.categoryId())
                .paymentMethod(request.paymentMethod())
                .sourceCorrelationId(request.sourceCorrelationId() != null ? request.sourceCorrelationId().trim() : null)
                .notes(request.notes())
                .version(0L)
                .build();

        payable = payableRepository.save(payable);
        log.info("Created payable {} (R$ {}) for tenant {}", payable.getId(), payable.getAmount(), tenantId);
        return mapToResponse(payable);
    }

    @Transactional
    public PayableDtos.Response settlePayable(UUID id, PayableDtos.Settle request, UUID tenantId, UUID userId) {
        FinancePayable payable = payableRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new PayableNotFoundException(id));

        if (payable.getStatus() == PayableStatus.PAID || payable.getStatus() == PayableStatus.CANCELLED) {
            throw new InvalidFinanceOperationException("Esta conta a pagar já foi liquidada ou cancelada.");
        }

        BigDecimal paymentAmount = request.paidAmount();
        BigDecimal newPaidAmount = payable.getPaidAmount().add(paymentAmount);

        if (newPaidAmount.compareTo(payable.getAmount()) >= 0) {
            payable.setStatus(PayableStatus.PAID);
            payable.setPaidAmount(payable.getAmount());
        } else {
            payable.setStatus(PayableStatus.PARTIALLY_PAID);
            payable.setPaidAmount(newPaidAmount);
        }

        LocalDate payDate = request.paymentDate() != null ? request.paymentDate() : LocalDate.now();
        payable.setPaidAt(Instant.now());
        payable.setAccountId(request.accountId());
        if (request.paymentMethod() != null) {
            payable.setPaymentMethod(request.paymentMethod().trim());
        }

        payable = payableRepository.save(payable);

        // Debit Account balance
        accountService.debitBalance(request.accountId(), paymentAmount, tenantId);

        // Register Transaction in Ledger
        FinanceTransaction transaction = FinanceTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(payable.getUnitId())
                .accountId(request.accountId())
                .categoryId(payable.getCategoryId())
                .payableId(payable.getId())
                .type(TransactionType.DEBIT)
                .amount(paymentAmount)
                .transactionDate(payDate)
                .competenceDate(payDate)
                .description("Pagamento: " + payable.getDescription())
                .sourceCorrelationId("SETTLE_PAY_" + payable.getId())
                .notes(request.notes())
                .createdByUserId(userId)
                .build();

        transactionRepository.save(transaction);
        log.info("Settled payable {} with amount R$ {} on account {}", id, paymentAmount, request.accountId());
        return mapToResponse(payable);
    }

    @Transactional
    public PayableDtos.Response cancelPayable(UUID id, String reason, UUID tenantId) {
        FinancePayable payable = payableRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new PayableNotFoundException(id));

        if (payable.getStatus() == PayableStatus.PAID) {
            throw new InvalidFinanceOperationException("Contas a pagar já liquidadas não podem ser canceladas diretamente.");
        }

        payable.setStatus(PayableStatus.CANCELLED);
        payable.setNotes(payable.getNotes() != null ? payable.getNotes() + " | Cancelado: " + reason : "Cancelado: " + reason);
        payable = payableRepository.save(payable);
        return mapToResponse(payable);
    }

    private PayableDtos.Response mapToResponse(FinancePayable p) {
        String accName = null;
        if (p.getAccountId() != null) {
            accName = accountRepository.findById(p.getAccountId()).map(FinanceAccount::getName).orElse(null);
        }

        String catName = null;
        if (p.getCategoryId() != null) {
            catName = categoryRepository.findById(p.getCategoryId()).map(FinanceCategory::getName).orElse(null);
        }

        return PayableDtos.Response.builder()
                .id(p.getId())
                .tenantId(p.getTenantId())
                .unitId(p.getUnitId())
                .supplierName(p.getSupplierName())
                .inventoryPurchaseId(p.getInventoryPurchaseId())
                .description(p.getDescription())
                .amount(p.getAmount())
                .paidAmount(p.getPaidAmount())
                .dueDate(p.getDueDate())
                .paidAt(p.getPaidAt())
                .status(p.getStatus())
                .paymentMethod(p.getPaymentMethod())
                .accountId(p.getAccountId())
                .accountName(accName)
                .categoryId(p.getCategoryId())
                .categoryName(catName)
                .sourceCorrelationId(p.getSourceCorrelationId())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
