package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolCategoryRequest;
import com.gomech.api.modules.tools.api.dto.ToolCategoryResponse;
import com.gomech.api.modules.tools.domain.ToolCategoryNotFoundException;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCategory;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolCategoryService {

    private final ToolCategoryRepository categoryRepository;

    @Transactional
    public ToolCategoryResponse createCategory(ToolCategoryRequest.Create request, UUID tenantId) {
        categoryRepository.findByTenantIdAndNameIgnoreCase(tenantId, request.name().trim())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Já existe uma categoria de ferramentas com o nome '" + request.name() + "'.");
                });

        ToolCategory category = ToolCategory.builder()
                .tenantId(tenantId)
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .requiresCalibration(Boolean.TRUE.equals(request.requiresCalibration()))
                .defaultMaintenanceIntervalDays(request.defaultMaintenanceIntervalDays())
                .build();

        category = categoryRepository.save(category);
        log.info("Created tool category id={} for tenant={}", category.getId(), tenantId);
        return mapToResponse(category);
    }

    @Transactional
    public ToolCategoryResponse updateCategory(UUID id, ToolCategoryRequest.Update request, UUID tenantId) {
        ToolCategory category = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ToolCategoryNotFoundException(id));

        category.setName(request.name().trim());
        category.setDescription(request.description() != null ? request.description().trim() : null);
        if (request.requiresCalibration() != null) {
            category.setRequiresCalibration(request.requiresCalibration());
        }
        if (request.defaultMaintenanceIntervalDays() != null) {
            category.setDefaultMaintenanceIntervalDays(request.defaultMaintenanceIntervalDays());
        }

        category = categoryRepository.save(category);
        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public List<ToolCategoryResponse> listCategories(UUID tenantId) {
        return categoryRepository.findByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ToolCategoryResponse getCategory(UUID id, UUID tenantId) {
        return categoryRepository.findByIdAndTenantId(id, tenantId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ToolCategoryNotFoundException(id));
    }

    @Transactional
    public void deleteCategory(UUID id, UUID tenantId) {
        ToolCategory category = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ToolCategoryNotFoundException(id));
        categoryRepository.delete(category);
        log.info("Deleted tool category id={} for tenant={}", id, tenantId);
    }

    public ToolCategoryResponse mapToResponse(ToolCategory category) {
        return ToolCategoryResponse.builder()
                .id(category.getId())
                .tenantId(category.getTenantId())
                .name(category.getName())
                .description(category.getDescription())
                .requiresCalibration(category.isRequiresCalibration())
                .defaultMaintenanceIntervalDays(category.getDefaultMaintenanceIntervalDays())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
