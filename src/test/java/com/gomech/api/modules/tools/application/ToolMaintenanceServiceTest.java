package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolMaintenanceDtos;
import com.gomech.api.modules.tools.domain.MaintenanceStatus;
import com.gomech.api.modules.tools.domain.MaintenanceType;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolMaintenance;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCategoryRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCustodyLogRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolMaintenanceRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolMaintenanceServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private ToolMaintenanceRepository maintenanceRepository;

    @Mock
    private ToolCategoryRepository categoryRepository;

    @Mock
    private ToolCustodyLogRepository custodyLogRepository;

    @InjectMocks
    private ToolMaintenanceService maintenanceService;

    private UUID tenantId;
    private UUID unitId;
    private Tool tool;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();

        tool = Tool.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .assetTag("MAN-01")
                .name("Manômetro de Pressão de Combustível")
                .status(ToolStatus.AVAILABLE)
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Should schedule tool calibration and update tool to IN_MAINTENANCE")
    void shouldScheduleMaintenanceSuccessfully() {
        ToolMaintenanceDtos.Schedule request = ToolMaintenanceDtos.Schedule.builder()
                .toolId(tool.getId())
                .maintenanceType(MaintenanceType.CALIBRATION)
                .scheduledDate(LocalDate.now().plusDays(5))
                .performedByProvider("LabCalibra")
                .estimatedCost(BigDecimal.valueOf(250.00))
                .description("Calibração anual metrológica")
                .build();

        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));
        when(maintenanceRepository.save(any(ToolMaintenance.class))).thenAnswer(inv -> {
            ToolMaintenance m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        ToolMaintenanceDtos.Response response = maintenanceService.scheduleMaintenance(request, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.maintenanceType()).isEqualTo(MaintenanceType.CALIBRATION);
        assertThat(response.status()).isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.IN_MAINTENANCE);
        verify(custodyLogRepository).save(any(ToolCustodyLog.class));
    }

    @Test
    @DisplayName("Should complete maintenance restoring tool to AVAILABLE and updating next due date")
    void shouldCompleteMaintenanceSuccessfully() {
        UUID maintenanceId = UUID.randomUUID();
        ToolMaintenance maintenance = ToolMaintenance.builder()
                .id(maintenanceId)
                .tenantId(tenantId)
                .unitId(unitId)
                .toolId(tool.getId())
                .maintenanceType(MaintenanceType.CALIBRATION)
                .status(MaintenanceStatus.SCHEDULED)
                .build();

        tool.setStatus(ToolStatus.IN_MAINTENANCE);

        ToolMaintenanceDtos.Complete request = ToolMaintenanceDtos.Complete.builder()
                .performedByProvider("LabCalibra")
                .cost(BigDecimal.valueOf(280.00))
                .findings("Aferido conforme norma ISO 17025. Incerteza: 0.1 bar.")
                .nextDueDate(LocalDate.now().plusMonths(12))
                .build();

        when(maintenanceRepository.findByIdAndTenantId(maintenanceId, tenantId)).thenReturn(Optional.of(maintenance));
        when(maintenanceRepository.save(any(ToolMaintenance.class))).thenReturn(maintenance);
        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));

        ToolMaintenanceDtos.Response response = maintenanceService.completeMaintenance(maintenanceId, request, tenantId, UUID.randomUUID());

        assertThat(response.status()).isEqualTo(MaintenanceStatus.COMPLETED);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.AVAILABLE);
        assertThat(tool.getLastMaintenanceAt()).isNotNull();
        assertThat(tool.getNextMaintenanceDueAt()).isNotNull();
        verify(custodyLogRepository).save(any(ToolCustodyLog.class));
    }
}
