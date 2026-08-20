package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.ReceivableDtos;
import com.gomech.api.modules.finance.domain.InvalidFinanceOperationException;
import com.gomech.api.modules.finance.domain.ReceivableNotFoundException;
import com.gomech.api.modules.finance.domain.ReceivableStatus;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceAccount;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceCategory;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceReceivable;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceAccountRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceCategoryRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceReceivableRepository;
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
public class ReceivableService {

    private final FinanceReceivableRepository receivableRepository;
    private final FinanceAccountRepository accountRepository;
    private final FinanceCategoryRepository categoryRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final FinanceAccountService accountService;

    @Transactional(readOnly = true)
    public Page<ReceivableDtos.Response> listReceivables(UUID tenantId, UUID unitId, ReceivableStatus status, Pageable pageable) {
        Page<FinanceReceivable> page;
        if (unitId != null && status != null) {
            page = receivableRepository.findAllByTenantIdAndUnitIdAndStatus(tenantId, unitId, status, pageable);
        } else if (unitId != null) {
            page = receivableRepository.findAllByTenantIdAndUnitId(tenantId, unitId, pageable);
        } else if (status != null) {
            page = receivableRepository.findAllByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            page = receivableRepository.findAllByTenantId(tenantId, pageable);
        }

        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ReceivableDtos.Response getReceivable(UUID id, UUID tenantId) {
        FinanceReceivable rec = receivableRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ReceivableNotFoundException(id));
        return mapToResponse(rec);
    }

    @Transactional
    public ReceivableDtos.Response createReceivable(ReceivableDtos.Create request, UUID tenantId) {
        if (request.sourceCorrelationId() != null && !request.sourceCorrelationId().isBlank()) {
            Optional<FinanceReceivable> existing = receivableRepository.findByTenantIdAndSourceCorrelationId(
                    tenantId, request.sourceCorrelationId().trim());
            if (existing.isPresent()) {
                log.warn("Receivable already exists for correlation ID: {}", request.sourceCorrelationId());
                return mapToResponse(existing.get());
            }
        }

        FinanceReceivable receivable = FinanceReceivable.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(request.unitId())
                .customerId(request.customerId())
                .customerName(request.customerName())
                .workOrderId(request.workOrderId())
                .orderNumber(request.orderNumber())
                .description(request.description().trim())
                .amount(request.amount())
                .paidAmount(BigDecimal.ZERO)
                .dueDate(request.dueDate())
                .status(ReceivableStatus.PENDING)
                .categoryId(request.categoryId())
                .paymentMethod(request.paymentMethod())
                .sourceCorrelationId(request.sourceCorrelationId() != null ? request.sourceCorrelationId().trim() : null)
                .notes(request.notes())
                .version(0L)
                .build();

        receivable = receivableRepository.save(receivable);
        log.info("Created receivable {} (R$ {}) for tenant {}", receivable.getId(), receivable.getAmount(), tenantId);
        return mapToResponse(receivable);
    }

    @Transactional
    public ReceivableDtos.Response settleReceivable(UUID id, ReceivableDtos.Settle request, UUID tenantId, UUID userId) {
        FinanceReceivable receivable = receivableRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ReceivableNotFoundException(id));

        if (receivable.getStatus() == ReceivableStatus.RECEIVED || receivable.getStatus() == ReceivableStatus.CANCELLED || receivable.getStatus() == ReceivableStatus.REVERSED) {
            throw new InvalidFinanceOperationException("Este título já foi liquidado, cancelado ou estornado.");
        }

        BigDecimal paymentAmount = request.paidAmount();
        BigDecimal newPaidAmount = receivable.getPaidAmount().add(paymentAmount);

        if (newPaidAmount.compareTo(receivable.getAmount()) >= 0) {
            receivable.setStatus(ReceivableStatus.RECEIVED);
            receivable.setPaidAmount(receivable.getAmount());
        } else {
            receivable.setStatus(ReceivableStatus.PARTIALLY_RECEIVED);
            receivable.setPaidAmount(newPaidAmount);
        }

        LocalDate payDate = request.paymentDate() != null ? request.paymentDate() : LocalDate.now();
        receivable.setReceivedAt(Instant.now());
        receivable.setAccountId(request.accountId());
        if (request.paymentMethod() != null) {
            receivable.setPaymentMethod(request.paymentMethod().trim());
        }

        receivable = receivableRepository.save(receivable);

        // Credit Account balance
        accountService.creditBalance(request.accountId(), paymentAmount, tenantId);

        // Register Transaction in Ledger
        FinanceTransaction transaction = FinanceTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(receivable.getUnitId())
                .accountId(request.accountId())
                .categoryId(receivable.getCategoryId())
                .receivableId(receivable.getId())
                .type(TransactionType.CREDIT)
                .amount(paymentAmount)
                .transactionDate(payDate)
                .competenceDate(payDate)
                .description("Recebimento: " + receivable.getDescription())
                .sourceCorrelationId("SETTLE_REC_" + receivable.getId())
                .notes(request.notes())
                .createdByUserId(userId)
                .build();

        transactionRepository.save(transaction);
        log.info("Settled receivable {} with amount R$ {} on account {}", id, paymentAmount, request.accountId());
        return mapToResponse(receivable);
    }

    @Transactional
    public ReceivableDtos.Response cancelReceivable(UUID id, String reason, UUID tenantId) {
        FinanceReceivable receivable = receivableRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ReceivableNotFoundException(id));

        if (receivable.getStatus() == ReceivableStatus.RECEIVED) {
            throw new InvalidFinanceOperationException("Títulos já recebidos devem ser estornados através do fluxo de compensação.");
        }

        receivable.setStatus(ReceivableStatus.CANCELLED);
        receivable.setNotes(receivable.getNotes() != null ? receivable.getNotes() + " | Cancelado: " + reason : "Cancelado: " + reason);
        receivable = receivableRepository.save(receivable);
        return mapToResponse(receivable);
    }

    @Transactional
    public void reverseReceivableForWorkOrder(UUID workOrderId, String reason, UUID tenantId) {
        Optional<FinanceReceivable> opt = receivableRepository.findByTenantIdAndWorkOrderId(tenantId, workOrderId);
        if (opt.isEmpty()) {
            log.warn("No receivable found for work order {} to reverse", workOrderId);
            return;
        }

        FinanceReceivable receivable = opt.get();
        if (receivable.getStatus() == ReceivableStatus.RECEIVED || receivable.getStatus() == ReceivableStatus.PARTIALLY_RECEIVED) {
            // Reversal of settled receivable: debit the account that received the payment
            if (receivable.getAccountId() != null && receivable.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                accountService.debitBalance(receivable.getAccountId(), receivable.getPaidAmount(), tenantId);

                // Register compensating debit transaction
                FinanceTransaction compTx = FinanceTransaction.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .unitId(receivable.getUnitId())
                        .accountId(receivable.getAccountId())
                        .categoryId(receivable.getCategoryId())
                        .receivableId(receivable.getId())
                        .type(TransactionType.DEBIT)
                        .amount(receivable.getPaidAmount())
                        .transactionDate(LocalDate.now())
                        .competenceDate(receivable.getDueDate())
                        .description("Estorno/Compensação: " + receivable.getDescription() + " (Motivo: " + reason + ")")
                        .sourceCorrelationId("REVERSE_REC_" + receivable.getId())
                        .build();

                transactionRepository.save(compTx);
            }
        }

        receivable.setStatus(ReceivableStatus.REVERSED);
        receivable.setNotes(receivable.getNotes() != null ? receivable.getNotes() + " | Estorno: " + reason : "Estorno: " + reason);
        receivableRepository.save(receivable);
        log.info("Reversed receivable {} for work order {}", receivable.getId(), workOrderId);
    }

    private ReceivableDtos.Response mapToResponse(FinanceReceivable r) {
        String accName = null;
        if (r.getAccountId() != null) {
            accName = accountRepository.findById(r.getAccountId()).map(FinanceAccount::getName).orElse(null);
        }

        String catName = null;
        if (r.getCategoryId() != null) {
            catName = categoryRepository.findById(r.getCategoryId()).map(FinanceCategory::getName).orElse(null);
        }

        return ReceivableDtos.Response.builder()
                .id(r.getId())
                .tenantId(r.getTenantId())
                .unitId(r.getUnitId())
                .customerId(r.getCustomerId())
                .customerName(r.getCustomerName())
                .workOrderId(r.getWorkOrderId())
                .orderNumber(r.getOrderNumber())
                .description(r.getDescription())
                .amount(r.getAmount())
                .paidAmount(r.getPaidAmount())
                .dueDate(r.getDueDate())
                .receivedAt(r.getReceivedAt())
                .status(r.getStatus())
                .paymentMethod(r.getPaymentMethod())
                .accountId(r.getAccountId())
                .accountName(accName)
                .categoryId(r.getCategoryId())
                .categoryName(catName)
                .sourceCorrelationId(r.getSourceCorrelationId())
                .notes(r.getNotes())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
