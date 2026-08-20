package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolCustodyDtos;
import com.gomech.api.modules.tools.domain.CustodyEventType;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.domain.ToolUnavailableException;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCustodyLogRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolCustodyServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private ToolCustodyLogRepository custodyLogRepository;

    @InjectMocks
    private ToolCustodyService custodyService;

    private UUID tenantId;
    private UUID unitId;
    private UUID mechanicId;
    private Tool tool;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        mechanicId = UUID.randomUUID();

        tool = Tool.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .assetTag("SCAN-01")
                .name("Scanner Automotivo Kaptor")
                .status(ToolStatus.AVAILABLE)
                .locationInUnit("Bancada Elétrica")
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Should check out tool to mechanic and log custody event")
    void shouldCheckOutToolSuccessfully() {
        ToolCustodyDtos.CheckOut request = ToolCustodyDtos.CheckOut.builder()
                .toolId(tool.getId())
                .mechanicUserId(mechanicId)
                .notes("Retirado para diagnóstico")
                .build();

        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));
        when(custodyLogRepository.save(any(ToolCustodyLog.class))).thenAnswer(inv -> {
            ToolCustodyLog l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        ToolCustodyDtos.CustodyLogResponse response = custodyService.checkOut(request, tenantId, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.eventType()).isEqualTo(CustodyEventType.CHECK_OUT);
        assertThat(response.toUserId()).isEqualTo(mechanicId);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.IN_USE);
        assertThat(tool.getCurrentHolderUserId()).isEqualTo(mechanicId);
        verify(toolRepository).save(tool);
    }

    @Test
    @DisplayName("Should throw ToolUnavailableException if tool is not AVAILABLE during checkout")
    void shouldThrowWhenCheckingOutUnavailableTool() {
        tool.setStatus(ToolStatus.IN_MAINTENANCE);
        ToolCustodyDtos.CheckOut request = ToolCustodyDtos.CheckOut.builder()
                .toolId(tool.getId())
                .mechanicUserId(mechanicId)
                .build();

        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));

        assertThatThrownBy(() -> custodyService.checkOut(request, tenantId, UUID.randomUUID()))
                .isInstanceOf(ToolUnavailableException.class);
    }

    @Test
    @DisplayName("Should check in tool restoring availability and clearing holder")
    void shouldCheckInToolSuccessfully() {
        tool.setStatus(ToolStatus.IN_USE);
        tool.setCurrentHolderUserId(mechanicId);

        ToolCustodyDtos.CheckIn request = ToolCustodyDtos.CheckIn.builder()
                .toolId(tool.getId())
                .locationInUnit("Gaveta 01")
                .notes("Devolução em perfeito estado")
                .build();

        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));
        when(custodyLogRepository.save(any(ToolCustodyLog.class))).thenAnswer(inv -> {
            ToolCustodyLog l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        ToolCustodyDtos.CustodyLogResponse response = custodyService.checkIn(request, tenantId, UUID.randomUUID());

        assertThat(response.eventType()).isEqualTo(CustodyEventType.CHECK_IN);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.AVAILABLE);
        assertThat(tool.getCurrentHolderUserId()).isNull();
        assertThat(tool.getLocationInUnit()).isEqualTo("Gaveta 01");
        verify(toolRepository).save(tool);
    }
}
