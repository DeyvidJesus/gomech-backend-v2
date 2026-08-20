package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolUsageDtos;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolUsage;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCustodyLogRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolUsageServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private ToolUsageRepository toolUsageRepository;

    @Mock
    private ToolCustodyLogRepository custodyLogRepository;

    @InjectMocks
    private ToolUsageService toolUsageService;

    private UUID tenantId;
    private UUID unitId;
    private UUID workOrderId;
    private UUID mechanicId;
    private Tool tool;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        workOrderId = UUID.randomUUID();
        mechanicId = UUID.randomUUID();

        tool = Tool.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .assetTag("ELEV-01")
                .name("Elevador Automotivo 4T")
                .status(ToolStatus.AVAILABLE)
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Should record tool usage on Work Order and update tool to IN_USE")
    void shouldRecordToolUsageSuccessfully() {
        ToolUsageDtos.RecordUsage request = ToolUsageDtos.RecordUsage.builder()
                .toolId(tool.getId())
                .workOrderId(workOrderId)
                .mechanicUserId(mechanicId)
                .notes("Suspensão dianteira")
                .build();

        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));
        when(toolUsageRepository.save(any(ToolUsage.class))).thenAnswer(inv -> {
            ToolUsage u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        ToolUsageDtos.UsageResponse response = toolUsageService.recordUsage(request, tenantId, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.workOrderId()).isEqualTo(workOrderId);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.IN_USE);
        verify(custodyLogRepository).save(any(ToolCustodyLog.class));
    }

    @Test
    @DisplayName("Should finish tool usage and return tool to AVAILABLE")
    void shouldFinishToolUsageSuccessfully() {
        UUID usageId = UUID.randomUUID();
        ToolUsage usage = ToolUsage.builder()
                .id(usageId)
                .tenantId(tenantId)
                .unitId(unitId)
                .toolId(tool.getId())
                .workOrderId(workOrderId)
                .mechanicUserId(mechanicId)
                .build();

        tool.setStatus(ToolStatus.IN_USE);
        tool.setCurrentHolderUserId(mechanicId);

        when(toolUsageRepository.findByIdAndTenantId(usageId, tenantId)).thenReturn(Optional.of(usage));
        when(toolUsageRepository.save(any(ToolUsage.class))).thenReturn(usage);
        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));

        ToolUsageDtos.UsageResponse response = toolUsageService.finishUsage(usageId, "Serviço finalizado", tenantId, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(usage.getCheckedInAt()).isNotNull();
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.AVAILABLE);
        assertThat(tool.getCurrentHolderUserId()).isNull();
    }
}
