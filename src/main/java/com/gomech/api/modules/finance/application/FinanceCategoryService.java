package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.CategoryDtos;
import com.gomech.api.modules.finance.domain.DreCategoryType;
import com.gomech.api.modules.finance.domain.FinanceCategoryNotFoundException;
import com.gomech.api.modules.finance.domain.TransactionType;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceCategory;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceCategoryService {

    private final FinanceCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryDtos.Response> listCategories(UUID tenantId, TransactionType type) {
        List<FinanceCategory> list = (type != null)
                ? categoryRepository.findAllByTenantIdAndType(tenantId, type)
                : categoryRepository.findAllByTenantId(tenantId);

        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDtos.Response getCategory(UUID id, UUID tenantId) {
        FinanceCategory cat = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new FinanceCategoryNotFoundException(id));
        return mapToResponse(cat);
    }

    @Transactional
    public CategoryDtos.Response createCategory(CategoryDtos.Create request, UUID tenantId) {
        FinanceCategory category = FinanceCategory.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name(request.name().trim())
                .type(request.type())
                .dreCategoryType(request.dreCategoryType())
                .isActive(true)
                .build();

        category = categoryRepository.save(category);
        log.info("Created finance category {} for tenant {}", category.getName(), tenantId);
        return mapToResponse(category);
    }

    @Transactional
    public CategoryDtos.Response updateCategory(UUID id, CategoryDtos.Update request, UUID tenantId) {
        FinanceCategory cat = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new FinanceCategoryNotFoundException(id));

        cat.setName(request.name().trim());
        cat.setType(request.type());
        cat.setDreCategoryType(request.dreCategoryType());
        if (request.isActive() != null) {
            cat.setIsActive(request.isActive());
        }

        cat = categoryRepository.save(cat);
        return mapToResponse(cat);
    }

    @Transactional
    public FinanceCategory getOrCreateDefaultCategory(UUID tenantId, String name, TransactionType type, DreCategoryType dreType) {
        return categoryRepository.findByTenantIdAndName(tenantId, name)
                .orElseGet(() -> categoryRepository.save(FinanceCategory.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .name(name)
                        .type(type)
                        .dreCategoryType(dreType)
                        .isActive(true)
                        .build()));
    }

    private CategoryDtos.Response mapToResponse(FinanceCategory c) {
        return CategoryDtos.Response.builder()
                .id(c.getId())
                .tenantId(c.getTenantId())
                .name(c.getName())
                .type(c.getType())
                .dreCategoryType(c.getDreCategoryType())
                .isActive(c.getIsActive())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
