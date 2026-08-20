package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolCustodyDtos;
import com.gomech.api.modules.tools.domain.CustodyEventType;
import com.gomech.api.modules.tools.domain.ToolNotFoundException;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.domain.ToolUnavailableException;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCustodyLogRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolCustodyService {

    private final ToolRepository toolRepository;
    private final ToolCustodyLogRepository custodyLogRepository;

    @Transactional
    public ToolCustodyDtos.CustodyLogResponse checkOut(ToolCustodyDtos.CheckOut request, UUID tenantId, UUID actorUserId) {
        Tool tool = toolRepository.findByIdAndTenantId(request.toolId(), tenantId)
                .orElseThrow(() -> new ToolNotFoundException(request.toolId()));

        if (tool.getStatus() != ToolStatus.AVAILABLE) {
            throw new ToolUnavailableException(tool.getId(), tool.getStatus());
        }

        UUID previousHolder = tool.getCurrentHolderUserId();
        tool.setStatus(ToolStatus.IN_USE);
        tool.setCurrentHolderUserId(request.mechanicUserId());
        toolRepository.save(tool);

        ToolCustodyLog logEntry = ToolCustodyLog.builder()
                .tenantId(tenantId)
                .unitId(tool.getUnitId())
                .toolId(tool.getId())
                .fromUserId(previousHolder)
                .toUserId(request.mechanicUserId())
                .eventType(CustodyEventType.CHECK_OUT)
                .notes(request.notes() != null ? request.notes().trim() : (request.workOrderId() != null ? "Retirado para OS " + request.workOrderId() : "Retirada de ferramenta"))
                .build();

        logEntry = custodyLogRepository.save(logEntry);
        log.info("Checked out tool id={} to user={} for tenant={}", tool.getId(), request.mechanicUserId(), tenantId);
        return mapToResponse(logEntry, tool);
    }

    @Transactional
    public ToolCustodyDtos.CustodyLogResponse checkIn(ToolCustodyDtos.CheckIn request, UUID tenantId, UUID actorUserId) {
        Tool tool = toolRepository.findByIdAndTenantId(request.toolId(), tenantId)
                .orElseThrow(() -> new ToolNotFoundException(request.toolId()));

        UUID previousHolder = tool.getCurrentHolderUserId();
        tool.setStatus(ToolStatus.AVAILABLE);
        tool.setCurrentHolderUserId(null);
        if (request.locationInUnit() != null && !request.locationInUnit().isBlank()) {
            tool.setLocationInUnit(request.locationInUnit().trim());
        }
        toolRepository.save(tool);

        ToolCustodyLog logEntry = ToolCustodyLog.builder()
                .tenantId(tenantId)
                .unitId(tool.getUnitId())
                .toolId(tool.getId())
                .fromUserId(previousHolder)
                .toUserId(null)
                .eventType(CustodyEventType.CHECK_IN)
                .notes(request.notes() != null ? request.notes().trim() : "Devolução de ferramenta ao armário")
                .build();

        logEntry = custodyLogRepository.save(logEntry);
        log.info("Checked in tool id={} by actor={} for tenant={}", tool.getId(), actorUserId, tenantId);
        return mapToResponse(logEntry, tool);
    }

    @Transactional
    public ToolCustodyDtos.CustodyLogResponse assignTool(ToolCustodyDtos.Assign request, UUID tenantId, UUID actorUserId) {
        Tool tool = toolRepository.findByIdAndTenantId(request.toolId(), tenantId)
                .orElseThrow(() -> new ToolNotFoundException(request.toolId()));

        UUID previousHolder = tool.getCurrentHolderUserId();
        tool.setCurrentHolderUserId(request.toUserId());
        tool.setStatus(ToolStatus.IN_USE);
        toolRepository.save(tool);

        ToolCustodyLog logEntry = ToolCustodyLog.builder()
                .tenantId(tenantId)
                .unitId(tool.getUnitId())
                .toolId(tool.getId())
                .fromUserId(previousHolder)
                .toUserId(request.toUserId())
                .eventType(CustodyEventType.ASSIGN)
                .notes(request.notes() != null ? request.notes().trim() : "Transferência direta de custódia")
                .build();

        logEntry = custodyLogRepository.save(logEntry);
        return mapToResponse(logEntry, tool);
    }

    @Transactional(readOnly = true)
    public List<ToolCustodyDtos.CustodyLogResponse> getToolHistory(UUID toolId, UUID tenantId) {
        Tool tool = toolRepository.findByIdAndTenantId(toolId, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(toolId));

        return custodyLogRepository.findByTenantIdAndToolIdOrderByCreatedAtDesc(tenantId, toolId).stream()
                .map(l -> mapToResponse(l, tool))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ToolCustodyDtos.CustodyLogResponse> listAllLogs(UUID tenantId, UUID toolId, Pageable pageable) {
        return custodyLogRepository.findAllByTenantId(tenantId, toolId, pageable)
                .map(l -> {
                    Tool tool = toolRepository.findByIdAndTenantId(l.getToolId(), tenantId).orElse(null);
                    return mapToResponse(l, tool);
                });
    }

    private ToolCustodyDtos.CustodyLogResponse mapToResponse(ToolCustodyLog log, Tool tool) {
        return ToolCustodyDtos.CustodyLogResponse.builder()
                .id(log.getId())
                .tenantId(log.getTenantId())
                .unitId(log.getUnitId())
                .toolId(log.getToolId())
                .toolName(tool != null ? tool.getName() : "Ferramenta")
                .toolAssetTag(tool != null ? tool.getAssetTag() : "-")
                .fromUserId(log.getFromUserId())
                .toUserId(log.getToUserId())
                .eventType(log.getEventType())
                .notes(log.getNotes())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
