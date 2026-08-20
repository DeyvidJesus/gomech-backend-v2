package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolUsageDtos;
import com.gomech.api.modules.tools.domain.CustodyEventType;
import com.gomech.api.modules.tools.domain.ToolNotFoundException;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.domain.ToolUnavailableException;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolUsage;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCustodyLogRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolUsageService {

    private final ToolRepository toolRepository;
    private final ToolUsageRepository toolUsageRepository;
    private final ToolCustodyLogRepository custodyLogRepository;

    @Transactional
    public ToolUsageDtos.UsageResponse recordUsage(ToolUsageDtos.RecordUsage request, UUID tenantId, UUID actorUserId) {
        Tool tool = toolRepository.findByIdAndTenantId(request.toolId(), tenantId)
                .orElseThrow(() -> new ToolNotFoundException(request.toolId()));

        if (tool.getStatus() != ToolStatus.AVAILABLE && tool.getStatus() != ToolStatus.IN_USE) {
            throw new ToolUnavailableException(tool.getId(), tool.getStatus());
        }

        UUID previousHolder = tool.getCurrentHolderUserId();
        tool.setStatus(ToolStatus.IN_USE);
        if (request.mechanicUserId() != null) {
            tool.setCurrentHolderUserId(request.mechanicUserId());
        }
        toolRepository.save(tool);

        ToolUsage usage = ToolUsage.builder()
                .tenantId(tenantId)
                .unitId(tool.getUnitId())
                .toolId(tool.getId())
                .workOrderId(request.workOrderId())
                .mechanicUserId(request.mechanicUserId())
                .notes(request.notes() != null ? request.notes().trim() : null)
                .build();

        usage = toolUsageRepository.save(usage);

        ToolCustodyLog logEntry = ToolCustodyLog.builder()
                .tenantId(tenantId)
                .unitId(tool.getUnitId())
                .toolId(tool.getId())
                .fromUserId(previousHolder)
                .toUserId(request.mechanicUserId())
                .eventType(CustodyEventType.CHECK_OUT)
                .notes("Uso registrado na Ordem de Serviço " + request.workOrderId())
                .build();

        custodyLogRepository.save(logEntry);
        log.info("Recorded tool usage toolId={} workOrderId={} for tenant={}", tool.getId(), request.workOrderId(), tenantId);
        return mapToResponse(usage, tool);
    }

    @Transactional
    public ToolUsageDtos.UsageResponse finishUsage(UUID usageId, String notes, UUID tenantId, UUID actorUserId) {
        ToolUsage usage = toolUsageRepository.findByIdAndTenantId(usageId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tool usage not found with ID: " + usageId));

        if (usage.getCheckedInAt() != null) {
            Tool tool = toolRepository.findByIdAndTenantId(usage.getToolId(), tenantId).orElse(null);
            return mapToResponse(usage, tool);
        }

        usage.setCheckedInAt(Instant.now());
        if (notes != null && !notes.isBlank()) {
            usage.setNotes(usage.getNotes() != null ? usage.getNotes() + " | Devolução: " + notes.trim() : notes.trim());
        }
        usage = toolUsageRepository.save(usage);

        Tool tool = toolRepository.findByIdAndTenantId(usage.getToolId(), tenantId).orElse(null);
        if (tool != null) {
            tool.setStatus(ToolStatus.AVAILABLE);
            tool.setCurrentHolderUserId(null);
            toolRepository.save(tool);

            ToolCustodyLog logEntry = ToolCustodyLog.builder()
                    .tenantId(tenantId)
                    .unitId(tool.getUnitId())
                    .toolId(tool.getId())
                    .fromUserId(usage.getMechanicUserId())
                    .toUserId(null)
                    .eventType(CustodyEventType.CHECK_IN)
                    .notes("Devolução de uso da OS " + usage.getWorkOrderId())
                    .build();

            custodyLogRepository.save(logEntry);
        }

        return mapToResponse(usage, tool);
    }

    @Transactional
    public void finishUsagesForWorkOrder(UUID workOrderId, UUID tenantId) {
        List<ToolUsage> activeUsages = toolUsageRepository.findByTenantIdAndWorkOrderId(tenantId, workOrderId).stream()
                .filter(u -> u.getCheckedInAt() == null)
                .toList();

        for (ToolUsage usage : activeUsages) {
            finishUsage(usage.getId(), "Conclusão automática por encerramento da OS " + workOrderId, tenantId, null);
        }
    }

    @Transactional(readOnly = true)
    public List<ToolUsageDtos.UsageResponse> listUsagesByTool(UUID toolId, UUID tenantId) {
        Tool tool = toolRepository.findByIdAndTenantId(toolId, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(toolId));

        return toolUsageRepository.findByTenantIdAndToolIdOrderByCheckedOutAtDesc(tenantId, toolId).stream()
                .map(u -> mapToResponse(u, tool))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ToolUsageDtos.UsageResponse> listUsagesByWorkOrder(UUID workOrderId, UUID tenantId) {
        return toolUsageRepository.findByTenantIdAndWorkOrderId(tenantId, workOrderId).stream()
                .map(u -> {
                    Tool tool = toolRepository.findByIdAndTenantId(u.getToolId(), tenantId).orElse(null);
                    return mapToResponse(u, tool);
                })
                .toList();
    }

    private ToolUsageDtos.UsageResponse mapToResponse(ToolUsage usage, Tool tool) {
        return ToolUsageDtos.UsageResponse.builder()
                .id(usage.getId())
                .tenantId(usage.getTenantId())
                .unitId(usage.getUnitId())
                .toolId(usage.getToolId())
                .toolName(tool != null ? tool.getName() : "Ferramenta")
                .toolAssetTag(tool != null ? tool.getAssetTag() : "-")
                .workOrderId(usage.getWorkOrderId())
                .mechanicUserId(usage.getMechanicUserId())
                .checkedOutAt(usage.getCheckedOutAt())
                .checkedInAt(usage.getCheckedInAt())
                .notes(usage.getNotes())
                .createdAt(usage.getCreatedAt())
                .build();
    }
}
