package com.gomech.api.modules.iam;

import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.core.entitlement.domain.QuotaExceededException;
import com.gomech.api.modules.iam.api.dto.CreateUnitRequest;
import com.gomech.api.modules.iam.api.dto.UnitResponse;
import com.gomech.api.modules.iam.application.UnitService;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Unit;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UnitRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private EntitlementService entitlementService;

    @InjectMocks
    private UnitService unitService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Deve criar unidade secundária com sucesso")
    void shouldCreateBranchUnitSuccessfully() {
        CreateUnitRequest request = new CreateUnitRequest(
                "Filial Norte",
                "Av. Norte, 100",
                false
        );

        when(entitlementService.checkQuota(eq(tenantId), eq(QuotaDimension.UNITS), eq(1L)))
                .thenReturn(QuotaDecision.allow(QuotaDimension.UNITS, 1, 3, "allowed"));

        when(unitRepository.save(any(Unit.class))).thenAnswer(inv -> {
            Unit u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UnitResponse response = unitService.createUnit(request, tenantId);
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Filial Norte");
        assertThat(response.isHeadquarters()).isFalse();
        assertThat(response.tenantId()).isEqualTo(tenantId);
        verify(entitlementService).recordUsage(eq(tenantId), eq(QuotaDimension.UNITS), eq(1L));
    }

    @Test
    @DisplayName("Deve rejeitar criação de unidade quando cota do plano for excedida")
    void shouldRejectUnitCreationWhenQuotaExceeded() {
        CreateUnitRequest request = new CreateUnitRequest(
                "Filial Excedente",
                "Av. Sul, 200",
                false
        );

        when(entitlementService.checkQuota(eq(tenantId), eq(QuotaDimension.UNITS), eq(1L)))
                .thenReturn(QuotaDecision.deny(QuotaDimension.UNITS, 1, 1, "quota_exceeded"));

        assertThatThrownBy(() -> unitService.createUnit(request, tenantId))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("Limite de unidades/filiais atingido");

        verify(unitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve desmarcar matriz anterior ao criar nova matriz")
    void shouldUnsetPreviousHeadquartersWhenCreatingNewHeadquarters() {
        Unit oldHq = new Unit();
        oldHq.setId(UUID.randomUUID());
        oldHq.setName("Matriz Antiga");
        oldHq.setHeadquarters(true);
        oldHq.setTenantId(tenantId);

        when(entitlementService.checkQuota(eq(tenantId), eq(QuotaDimension.UNITS), eq(1L)))
                .thenReturn(QuotaDecision.allow(QuotaDimension.UNITS, 1, 5, "allowed"));
        when(unitRepository.findAllByTenantId(tenantId)).thenReturn(List.of(oldHq));
        when(unitRepository.save(any(Unit.class))).thenAnswer(inv -> {
            Unit u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });

        CreateUnitRequest request = new CreateUnitRequest(
                "Nova Matriz",
                "Av. Principal, 500",
                true
        );

        UnitResponse response = unitService.createUnit(request, tenantId);
        assertThat(response.isHeadquarters()).isTrue();
        assertThat(oldHq.isHeadquarters()).isFalse();
        verify(unitRepository, atLeast(2)).save(any(Unit.class));
    }

    @Test
    @DisplayName("Deve rejeitar obtenção de unidade pertencente a outro tenant")
    void shouldRejectGettingUnitFromAnotherTenant() {
        UUID unitId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();

        Unit unit = new Unit();
        unit.setId(unitId);
        unit.setTenantId(otherTenantId);

        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> unitService.getUnitById(unitId, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pertence à oficina");
    }
}
