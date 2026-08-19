package com.gomech.api.modules.billing;

import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.modules.billing.application.UsageService;
import com.gomech.api.modules.billing.infrastructure.persistence.model.UsageRecord;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.UsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock
    private UsageRecordRepository usageRecordRepository;

    @InjectMocks
    private UsageService usageService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Deve criar novo registro de consumo quando não existir registro no ciclo atual")
    void shouldCreateNewUsageRecordWhenNoneExists() {
        when(usageRecordRepository.findCurrentPeriodUsage(eq(tenantId), eq(QuotaDimension.AI_USAGE.name()), any()))
                .thenReturn(Optional.empty());

        usageService.recordUsage(tenantId, null, QuotaDimension.AI_USAGE, 15L);

        ArgumentCaptor<UsageRecord> captor = ArgumentCaptor.forClass(UsageRecord.class);
        verify(usageRecordRepository).save(captor.capture());

        UsageRecord saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getDimension()).isEqualTo("AI_USAGE");
        assertThat(saved.getAmount()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Deve incrementar consumo em registro existente do ciclo")
    void shouldIncrementExistingUsageRecord() {
        UsageRecord existing = new UsageRecord();
        existing.setTenantId(tenantId);
        existing.setDimension("AI_USAGE");
        existing.setAmount(100L);

        when(usageRecordRepository.findCurrentPeriodUsage(eq(tenantId), eq(QuotaDimension.AI_USAGE.name()), any()))
                .thenReturn(Optional.of(existing));

        usageService.recordUsage(tenantId, null, QuotaDimension.AI_USAGE, 25L);

        verify(usageRecordRepository).save(existing);
        assertThat(existing.getAmount()).isEqualTo(125L);
    }

    @Test
    @DisplayName("Deve retornar consumo atual corretamente")
    void shouldReturnCurrentUsage() {
        UsageRecord existing = new UsageRecord();
        existing.setTenantId(tenantId);
        existing.setDimension("AI_USAGE");
        existing.setAmount(75L);

        when(usageRecordRepository.findCurrentPeriodUsage(eq(tenantId), eq(QuotaDimension.AI_USAGE.name()), any()))
                .thenReturn(Optional.of(existing));

        long usage = usageService.getCurrentUsage(tenantId, QuotaDimension.AI_USAGE);
        assertThat(usage).isEqualTo(75L);
    }
}
