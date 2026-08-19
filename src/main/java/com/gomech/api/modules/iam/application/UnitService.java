package com.gomech.api.modules.iam.application;

import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.core.entitlement.domain.QuotaExceededException;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.CreateUnitRequest;
import com.gomech.api.modules.iam.api.dto.UnitResponse;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Unit;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final EntitlementService entitlementService;

    @Transactional(readOnly = true)
    public List<UnitResponse> getUnits(UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        return unitRepository.findAllByTenantId(effectiveTenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitResponse getUnitById(UUID unitId, UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Unidade não encontrada: " + unitId));

        if (!unit.getTenantId().equals(effectiveTenantId)) {
            throw new IllegalArgumentException("Unidade não pertence à oficina autenticada");
        }

        return toResponse(unit);
    }

    @Transactional
    public UnitResponse createUnit(CreateUnitRequest request, UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();

        // Avaliação de cota de unidades / filiais via Core Entitlement
        QuotaDecision quotaDecision = entitlementService.checkQuota(effectiveTenantId, QuotaDimension.UNITS, 1);
        if (!quotaDecision.allowed()) {
            throw new QuotaExceededException(
                    QuotaDimension.UNITS,
                    quotaDecision.currentUsage(),
                    quotaDecision.limit(),
                    "Limite de unidades/filiais atingido para o plano da oficina. Limite: " + quotaDecision.limit()
            );
        }

        if (request.isHeadquarters()) {
            // Se esta nova unidade for matriz, desmarcar a matriz anterior
            List<Unit> existingUnits = unitRepository.findAllByTenantId(effectiveTenantId);
            for (Unit u : existingUnits) {
                if (u.isHeadquarters()) {
                    u.setHeadquarters(false);
                    unitRepository.save(u);
                }
            }
        }

        Unit unit = new Unit();
        unit.setTenantId(effectiveTenantId);
        unit.setName(request.name());
        unit.setAddress(request.address());
        unit.setHeadquarters(request.isHeadquarters());

        Unit savedUnit = unitRepository.save(unit);
        entitlementService.recordUsage(effectiveTenantId, QuotaDimension.UNITS, 1);
        log.info("Nova unidade '{}' ({}) criada com sucesso para o tenant {}", savedUnit.getName(), savedUnit.getId(), effectiveTenantId);
        return toResponse(savedUnit);
    }

    private UnitResponse toResponse(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getAddress(),
                unit.isHeadquarters(),
                unit.getTenantId()
        );
    }
}
