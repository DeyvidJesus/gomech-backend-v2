package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.ReceivableDtos;
import com.gomech.api.modules.finance.domain.ReceivableNotFoundException;
import com.gomech.api.modules.finance.domain.ReceivableStatus;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceReceivable;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceAccountRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceCategoryRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceReceivableRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivableServiceTest {

    @Mock
    private FinanceReceivableRepository receivableRepository;

    @Mock
    private FinanceAccountRepository accountRepository;

    @Mock
    private FinanceCategoryRepository categoryRepository;

    @Mock
    private FinanceTransactionRepository transactionRepository;

    @Mock
    private FinanceAccountService accountService;

    @InjectMocks
    private ReceivableService receivableService;

    private UUID tenantId;
    private UUID unitId;
    private UUID accountId;
    private FinanceReceivable receivable;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        receivable = FinanceReceivable.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .customerName("João Silva")
                .description("OS-0001 Revisão Geral")
                .amount(BigDecimal.valueOf(1500.00))
                .paidAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(15))
                .status(ReceivableStatus.PENDING)
                .sourceCorrelationId("WO_COMPLETED_123")
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("Should create receivable with PENDING status")
    void shouldCreateReceivableSuccessfully() {
        ReceivableDtos.Create request = ReceivableDtos.Create.builder()
                .unitId(unitId)
                .customerName("João Silva")
                .description("OS-0001 Revisão Geral")
                .amount(BigDecimal.valueOf(1500.00))
                .dueDate(LocalDate.now().plusDays(15))
                .sourceCorrelationId("WO_COMPLETED_123")
                .build();

        when(receivableRepository.findByTenantIdAndSourceCorrelationId(tenantId, "WO_COMPLETED_123")).thenReturn(Optional.empty());
        when(receivableRepository.save(any(FinanceReceivable.class))).thenAnswer(inv -> {
            FinanceReceivable r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReceivableDtos.Response response = receivableService.createReceivable(request, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualByComparingTo("1500.00");
        assertThat(response.status()).isEqualTo(ReceivableStatus.PENDING);
        verify(receivableRepository).save(any(FinanceReceivable.class));
    }

    @Test
    @DisplayName("Should settle receivable completely, credit account balance and log credit transaction")
    void shouldSettleReceivableSuccessfully() {
        ReceivableDtos.Settle request = ReceivableDtos.Settle.builder()
                .accountId(accountId)
                .paidAmount(BigDecimal.valueOf(1500.00))
                .paymentMethod("PIX")
                .notes("Pagamento integral via PIX")
                .build();

        when(receivableRepository.findByIdAndTenantId(receivable.getId(), tenantId)).thenReturn(Optional.of(receivable));
        when(receivableRepository.save(any(FinanceReceivable.class))).thenReturn(receivable);

        ReceivableDtos.Response response = receivableService.settleReceivable(receivable.getId(), request, tenantId, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(receivable.getStatus()).isEqualTo(ReceivableStatus.RECEIVED);
        assertThat(receivable.getPaidAmount()).isEqualByComparingTo("1500.00");
        verify(accountService).creditBalance(accountId, BigDecimal.valueOf(1500.00), tenantId);
        verify(transactionRepository).save(any(FinanceTransaction.class));
    }

    @Test
    @DisplayName("Should reverse receivable and create compensating debit transaction when WO is reopened")
    void shouldReverseSettledReceivable() {
        UUID workOrderId = UUID.randomUUID();
        receivable.setWorkOrderId(workOrderId);
        receivable.setStatus(ReceivableStatus.RECEIVED);
        receivable.setPaidAmount(BigDecimal.valueOf(1500.00));
        receivable.setAccountId(accountId);

        when(receivableRepository.findByTenantIdAndWorkOrderId(tenantId, workOrderId)).thenReturn(Optional.of(receivable));

        receivableService.reverseReceivableForWorkOrder(workOrderId, "Reabertura de OS", tenantId);

        assertThat(receivable.getStatus()).isEqualTo(ReceivableStatus.REVERSED);
        verify(accountService).debitBalance(accountId, BigDecimal.valueOf(1500.00), tenantId);
        verify(transactionRepository).save(any(FinanceTransaction.class));
        verify(receivableRepository).save(receivable);
    }
}
