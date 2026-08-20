package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolMaintenanceDtos;
import com.gomech.api.modules.tools.domain.CustodyEventType;
import com.gomech.api.modules.tools.domain.InvalidToolOperationException;
import com.gomech.api.modules.tools.domain.MaintenanceStatus;
import com.gomech.api.modules.tools.domain.MaintenanceType;
import com.gomech.api.modules.tools.domain.ToolNotFoundException;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCategory;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolMaintenance;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCategoryRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCustodyLogRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolMaintenanceRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolMaintenanceService {

    private final ToolRepository toolRepository;
    private final ToolMaintenanceRepository maintenanceRepository;
    private final ToolCategoryRepository categoryRepository;
    private final ToolCustodyLogRepository custodyLogRepository;

    @Transactional
    public ToolMaintenanceDtos.Response scheduleMaintenance(ToolMaintenanceDtos.Schedule request, UUID tenantId) {
        Tool tool = toolRepository.findByIdAndTenantId(request.toolId(), tenantId)
                .orElseThrow(() -> new ToolNotFoundException(request.toolId()));

        MaintenanceType type = request.maintenanceType() != null ? request.maintenanceType() : MaintenanceType.PREVENTIVE;

        tool.setStatus(ToolStatus.IN_MAINTENANCE);
        tool.setCurrentHolderUserId(null);
        toolRepository.save(tool);

        ToolMaintenance maintenance = ToolMaintenance.builder()
                .tenantId(tenantId)
                .unitId(tool.getUnitId())
                .toolId(tool.getId())
                .maintenanceType(type)
                .status(MaintenanceStatus.SCHEDULED)
                .scheduledDate(request.scheduledDate())
                .performedByProvider(request.performedByProvider() != null ? request.performedByProvider().trim() : null)
                .cost(request.estimatedCost())
                .description(request.description() != null ? request.description().trim() : null)
                .build();

        maintenance = maintenanceRepository.save(maintenance);

        ToolCustodyLog logEntry = ToolCustodyLog.builder()
                .tenantId(tenantId)
                .unitId(tool.getUnitId())
                .toolId(tool.getId())
                .eventType(CustodyEventType.TRANSFER)
                .notes("Envio para manutenção/calibração (" + type + "): " + (request.description() != null ? request.description() : ""))
                .build();

        custodyLogRepository.save(logEntry);
        log.info("Scheduled tool maintenance for toolId={} type={} for tenant={}", tool.getId(), type, tenantId);

        return mapToResponse(maintenance, tool);
    }

    @Transactional
    public ToolMaintenanceDtos.Response completeMaintenance(UUID maintenanceId, ToolMaintenanceDtos.Complete request, UUID tenantId, UUID actorUserId) {
        ToolMaintenance maintenance = maintenanceRepository.findByIdAndTenantId(maintenanceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found with ID: " + maintenanceId));

        if (maintenance.getStatus() == MaintenanceStatus.COMPLETED || maintenance.getStatus() == MaintenanceStatus.CANCELLED) {
            throw new InvalidToolOperationException("Este registro de manutenção já está finalizado.");
        }

        maintenance.setStatus(MaintenanceStatus.COMPLETED);
        maintenance.setPerformedAt(Instant.now());
        if (request.performedByProvider() != null) {
            maintenance.setPerformedByProvider(request.performedByProvider().trim());
        }
        if (request.cost() != null) {
            maintenance.setCost(request.cost());
        }
        if (request.description() != null) {
            maintenance.setDescription(request.description().trim());
        }
        if (request.findings() != null) {
            maintenance.setFindings(request.findings().trim());
        }
        if (request.nextDueDate() != null) {
            maintenance.setNextDueDate(request.nextDueDate());
        }

        maintenance = maintenanceRepository.save(maintenance);

        UUID toolId = maintenance.getToolId();
        Tool tool = toolRepository.findByIdAndTenantId(toolId, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(toolId));

        tool.setStatus(ToolStatus.AVAILABLE);
        tool.setLastMaintenanceAt(Instant.now());
        if (request.nextDueDate() != null) {
            tool.setNextMaintenanceDueAt(request.nextDueDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        } else if (tool.getCategoryId() != null) {
            ToolCategory cat = categoryRepository.findByIdAndTenantId(tool.getCategoryId(), tenantId).orElse(null);
            if (cat != null && cat.getDefaultMaintenanceIntervalDays() != null && cat.getDefaultMaintenanceIntervalDays() > 0) {
                tool.setNextMaintenanceDueAt(Instant.now().plus(cat.getDefaultMaintenanceIntervalDays(), ChronoUnit.DAYS));
            }
        }
        toolRepository.save(tool);

        ToolCustodyLog logEntry = ToolCustodyLog.builder()
                .tenantId(tenantId)
                .unitId(tool.getUnitId())
                .toolId(tool.getId())
                .eventType(CustodyEventType.RETURN)
                .notes("Retorno de manutenção/calibração concluída")
                .build();

        custodyLogRepository.save(logEntry);
        log.info("Completed tool maintenance id={} toolId={}", maintenance.getId(), tool.getId());

        return mapToResponse(maintenance, tool);
    }

    @Transactional(readOnly = true)
    public Page<ToolMaintenanceDtos.Response> listMaintenances(UUID tenantId, UUID unitId, MaintenanceStatus status, UUID toolId, Pageable pageable) {
        return maintenanceRepository.findAllFiltered(tenantId, unitId, status, toolId, pageable)
                .map(m -> {
                    Tool tool = toolRepository.findByIdAndTenantId(m.getToolId(), tenantId).orElse(null);
                    return mapToResponse(m, tool);
                });
    }

    @Transactional(readOnly = true)
    public List<ToolMaintenanceDtos.Response> getMaintenanceHistoryForTool(UUID toolId, UUID tenantId) {
        Tool tool = toolRepository.findByIdAndTenantId(toolId, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(toolId));

        return maintenanceRepository.findByTenantIdAndToolIdOrderByCreatedAtDesc(tenantId, toolId).stream()
                .map(m -> mapToResponse(m, tool))
                .toList();
    }

    private ToolMaintenanceDtos.Response mapToResponse(ToolMaintenance m, Tool tool) {
        return ToolMaintenanceDtos.Response.builder()
                .id(m.getId())
                .tenantId(m.getTenantId())
                .unitId(m.getUnitId())
                .toolId(m.getToolId())
                .toolName(tool != null ? tool.getName() : "Ferramenta")
                .toolAssetTag(tool != null ? tool.getAssetTag() : "-")
                .maintenanceType(m.getMaintenanceType())
                .status(m.getStatus())
                .scheduledDate(m.getScheduledDate())
                .performedAt(m.getPerformedAt())
                .performedByProvider(m.getPerformedByProvider())
                .cost(m.getCost())
                .description(m.getDescription())
                .findings(m.getFindings())
                .nextDueDate(m.getNextDueDate())
                .version(m.getVersion())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
