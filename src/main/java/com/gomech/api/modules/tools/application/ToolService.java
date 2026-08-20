package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.CreateToolRequest;
import com.gomech.api.modules.tools.api.dto.ToolResponse;
import com.gomech.api.modules.tools.api.dto.UpdateToolRequest;
import com.gomech.api.modules.tools.domain.ToolNotFoundException;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCategory;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCategoryRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolService {

    private final ToolRepository toolRepository;
    private final ToolCategoryRepository categoryRepository;

    @Transactional
    public ToolResponse createTool(CreateToolRequest request, UUID tenantId) {
        toolRepository.findByTenantIdAndAssetTag(tenantId, request.assetTag().trim())
                .ifPresent(t -> {
                    throw new IllegalArgumentException("Já existe uma ferramenta com a etiqueta de patrimônio '" + request.assetTag() + "'.");
                });

        Instant nextMaintenanceDue = null;
        if (request.initialMaintenanceIntervalDays() != null && request.initialMaintenanceIntervalDays() > 0) {
            nextMaintenanceDue = Instant.now().plus(request.initialMaintenanceIntervalDays(), ChronoUnit.DAYS);
        } else if (request.categoryId() != null) {
            ToolCategory cat = categoryRepository.findByIdAndTenantId(request.categoryId(), tenantId).orElse(null);
            if (cat != null && cat.getDefaultMaintenanceIntervalDays() != null && cat.getDefaultMaintenanceIntervalDays() > 0) {
                nextMaintenanceDue = Instant.now().plus(cat.getDefaultMaintenanceIntervalDays(), ChronoUnit.DAYS);
            }
        }

        Tool tool = Tool.builder()
                .tenantId(tenantId)
                .unitId(request.unitId())
                .categoryId(request.categoryId())
                .assetTag(request.assetTag().trim().toUpperCase())
                .serialNumber(request.serialNumber() != null ? request.serialNumber().trim() : null)
                .name(request.name().trim())
                .brand(request.brand() != null ? request.brand().trim() : null)
                .model(request.model() != null ? request.model().trim() : null)
                .status(ToolStatus.AVAILABLE)
                .locationInUnit(request.locationInUnit() != null ? request.locationInUnit().trim() : null)
                .purchaseDate(request.purchaseDate())
                .purchaseCost(request.purchaseCost())
                .nextMaintenanceDueAt(nextMaintenanceDue)
                .build();

        tool = toolRepository.save(tool);
        log.info("Created tool id={} assetTag={} for tenant={}", tool.getId(), tool.getAssetTag(), tenantId);
        return mapToResponse(tool, tenantId);
    }

    @Transactional
    public ToolResponse updateTool(UUID id, UpdateToolRequest request, UUID tenantId) {
        Tool tool = toolRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(id));

        if (!tool.getAssetTag().equalsIgnoreCase(request.assetTag().trim())) {
            toolRepository.findByTenantIdAndAssetTag(tenantId, request.assetTag().trim())
                    .ifPresent(t -> {
                        throw new IllegalArgumentException("Já existe uma ferramenta com o patrimônio '" + request.assetTag() + "'.");
                    });
            tool.setAssetTag(request.assetTag().trim().toUpperCase());
        }

        tool.setName(request.name().trim());
        tool.setCategoryId(request.categoryId());
        tool.setSerialNumber(request.serialNumber() != null ? request.serialNumber().trim() : null);
        tool.setBrand(request.brand() != null ? request.brand().trim() : null);
        tool.setModel(request.model() != null ? request.model().trim() : null);
        if (request.status() != null) {
            tool.setStatus(request.status());
        }
        tool.setLocationInUnit(request.locationInUnit() != null ? request.locationInUnit().trim() : null);
        tool.setPurchaseDate(request.purchaseDate());
        tool.setPurchaseCost(request.purchaseCost());

        tool = toolRepository.save(tool);
        return mapToResponse(tool, tenantId);
    }

    @Transactional(readOnly = true)
    public ToolResponse getTool(UUID id, UUID tenantId) {
        Tool tool = toolRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(id));
        return mapToResponse(tool, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<ToolResponse> listTools(
            UUID tenantId,
            UUID unitId,
            ToolStatus status,
            UUID categoryId,
            String search,
            Pageable pageable
    ) {
        Page<Tool> toolsPage = toolRepository.findAllFiltered(tenantId, unitId, status, categoryId, search, pageable);
        Map<UUID, String> categoriesMap = categoryRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(ToolCategory::getId, ToolCategory::getName));

        return toolsPage.map(t -> mapToResponseWithCategories(t, categoriesMap));
    }

    @Transactional(readOnly = true)
    public List<ToolResponse> listAvailableTools(UUID tenantId, UUID unitId) {
        Map<UUID, String> categoriesMap = categoryRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(ToolCategory::getId, ToolCategory::getName));

        return toolRepository.findAvailableByUnitId(tenantId, unitId).stream()
                .map(t -> mapToResponseWithCategories(t, categoriesMap))
                .toList();
    }

    @Transactional
    public void deleteTool(UUID id, UUID tenantId) {
        Tool tool = toolRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(id));
        tool.setDeletedAt(Instant.now());
        toolRepository.save(tool);
        log.info("Soft-deleted tool id={} for tenant={}", id, tenantId);
    }

    public ToolResponse mapToResponse(Tool tool, UUID tenantId) {
        String categoryName = null;
        if (tool.getCategoryId() != null) {
            categoryName = categoryRepository.findByIdAndTenantId(tool.getCategoryId(), tenantId)
                    .map(ToolCategory::getName)
                    .orElse(null);
        }
        return mapToResponseWithCategoryName(tool, categoryName);
    }

    private ToolResponse mapToResponseWithCategories(Tool tool, Map<UUID, String> categoriesMap) {
        String categoryName = tool.getCategoryId() != null ? categoriesMap.get(tool.getCategoryId()) : null;
        return mapToResponseWithCategoryName(tool, categoryName);
    }

    private ToolResponse mapToResponseWithCategoryName(Tool tool, String categoryName) {
        boolean overdue = tool.getNextMaintenanceDueAt() != null && tool.getNextMaintenanceDueAt().isBefore(Instant.now());

        return ToolResponse.builder()
                .id(tool.getId())
                .tenantId(tool.getTenantId())
                .unitId(tool.getUnitId())
                .categoryId(tool.getCategoryId())
                .categoryName(categoryName)
                .assetTag(tool.getAssetTag())
                .serialNumber(tool.getSerialNumber())
                .name(tool.getName())
                .brand(tool.getBrand())
                .model(tool.getModel())
                .status(tool.getStatus())
                .currentHolderUserId(tool.getCurrentHolderUserId())
                .locationInUnit(tool.getLocationInUnit())
                .purchaseDate(tool.getPurchaseDate())
                .purchaseCost(tool.getPurchaseCost())
                .lastMaintenanceAt(tool.getLastMaintenanceAt())
                .nextMaintenanceDueAt(tool.getNextMaintenanceDueAt())
                .maintenanceOverdue(overdue)
                .version(tool.getVersion())
                .createdAt(tool.getCreatedAt())
                .updatedAt(tool.getUpdatedAt())
                .build();
    }
}
