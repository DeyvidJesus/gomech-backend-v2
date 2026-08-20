package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.PayableDtos;
import com.gomech.api.modules.finance.domain.PayableNotFoundException;
import com.gomech.api.modules.finance.domain.PayableStatus;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinancePayable;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceTransaction;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceAccountRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceCategoryRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinancePayableRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayableServiceTest {

    @Mock
    private FinancePayableRepository payableRepository;

    @Mock
    private FinanceAccountRepository accountRepository;

    @Mock
    private FinanceCategoryRepository categoryRepository;

    @Mock
    private FinanceTransactionRepository transactionRepository;

    @Mock
    private FinanceAccountService accountService;

    @InjectMocks
    private PayableService payableService;

    private UUID tenantId;
    private UUID unitId;
    private UUID accountId;
    private FinancePayable payable;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        payable = FinancePayable.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .supplierName("Distribuidora de Peças ABC")
                .description("NF 12345 - Pastilhas e Discos")
                .amount(BigDecimal.valueOf(800.00))
                .paidAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(30))
                .status(PayableStatus.PENDING)
                .sourceCorrelationId("INV_PURCHASE_999")
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("Should create payable with PENDING status")
    void shouldCreatePayableSuccessfully() {
        PayableDtos.Create request = PayableDtos.Create.builder()
                .unitId(unitId)
                .supplierName("Distribuidora de Peças ABC")
                .description("NF 12345 - Pastilhas e Discos")
                .amount(BigDecimal.valueOf(800.00))
                .dueDate(LocalDate.now().plusDays(30))
                .sourceCorrelationId("INV_PURCHASE_999")
                .build();

        when(payableRepository.findByTenantIdAndSourceCorrelationId(tenantId, "INV_PURCHASE_999")).thenReturn(Optional.empty());
        when(payableRepository.save(any(FinancePayable.class))).thenAnswer(inv -> {
            FinancePayable p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PayableDtos.Response response = payableService.createPayable(request, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualByComparingTo("800.00");
        assertThat(response.status()).isEqualTo(PayableStatus.PENDING);
        verify(payableRepository).save(any(FinancePayable.class));
    }

    @Test
    @DisplayName("Should settle payable, debit account balance and record debit transaction")
    void shouldSettlePayableSuccessfully() {
        PayableDtos.Settle request = PayableDtos.Settle.builder()
                .accountId(accountId)
                .paidAmount(BigDecimal.valueOf(800.00))
                .paymentMethod("BOLETO")
                .notes("Boleto bancário quitado")
                .build();

        when(payableRepository.findByIdAndTenantId(payable.getId(), tenantId)).thenReturn(Optional.of(payable));
        when(payableRepository.save(any(FinancePayable.class))).thenReturn(payable);

        PayableDtos.Response response = payableService.settlePayable(payable.getId(), request, tenantId, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(payable.getStatus()).isEqualTo(PayableStatus.PAID);
        assertThat(payable.getPaidAmount()).isEqualByComparingTo("800.00");
        verify(accountService).debitBalance(accountId, BigDecimal.valueOf(800.00), tenantId);
        verify(transactionRepository).save(any(FinanceTransaction.class));
    }
}
