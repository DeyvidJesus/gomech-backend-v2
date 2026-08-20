package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.ToolsContract;
import com.gomech.api.modules.tools.api.dto.ToolUsageDtos;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ToolsContractImpl implements ToolsContract {

    private final ToolUsageService toolUsageService;
    private final ToolRepository toolRepository;

    @Override
    public void recordWorkOrderToolUsage(UUID toolId, UUID workOrderId, UUID mechanicUserId, UUID tenantId) {
        toolUsageService.recordUsage(
                ToolUsageDtos.RecordUsage.builder()
                        .toolId(toolId)
                        .workOrderId(workOrderId)
                        .mechanicUserId(mechanicUserId)
                        .notes("Vinculado automaticamente via contrato de OS")
                        .build(),
                tenantId,
                null
        );
    }

    @Override
    public void releaseWorkOrderTools(UUID workOrderId, UUID tenantId) {
        toolUsageService.finishUsagesForWorkOrder(workOrderId, tenantId);
    }

    @Override
    public boolean isToolAvailable(UUID toolId, UUID tenantId) {
        return toolRepository.findByIdAndTenantId(toolId, tenantId)
                .map(t -> t.getStatus() == ToolStatus.AVAILABLE)
                .orElse(false);
    }
}
