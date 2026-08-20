package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolTransferDtos;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.domain.ToolTransferStatus;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolTransfer;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCustodyLogRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolTransferRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolTransferServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private ToolTransferRepository transferRepository;

    @Mock
    private ToolCustodyLogRepository custodyLogRepository;

    @InjectMocks
    private ToolTransferService transferService;

    private UUID tenantId;
    private UUID sourceUnitId;
    private UUID destUnitId;
    private Tool tool;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sourceUnitId = UUID.randomUUID();
        destUnitId = UUID.randomUUID();

        tool = Tool.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(sourceUnitId)
                .assetTag("MULT-01")
                .name("Multímetro Digital")
                .status(ToolStatus.AVAILABLE)
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Should create inter-branch tool transfer and update tool to IN_TRANSIT")
    void shouldCreateTransferSuccessfully() {
        ToolTransferDtos.Create request = ToolTransferDtos.Create.builder()
                .toolId(tool.getId())
                .destinationUnitId(destUnitId)
                .notes("Transferência temporária")
                .build();

        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));
        when(transferRepository.countByTenantId(tenantId)).thenReturn(0L);
        when(transferRepository.save(any(ToolTransfer.class))).thenAnswer(inv -> {
            ToolTransfer t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        ToolTransferDtos.Response response = transferService.createTransfer(request, tenantId, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.transferNumber()).isEqualTo("TRFT-00001");
        assertThat(response.status()).isEqualTo(ToolTransferStatus.IN_TRANSIT);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.IN_TRANSIT);
        verify(custodyLogRepository).save(any(ToolCustodyLog.class));
    }

    @Test
    @DisplayName("Should complete transfer updating tool unit to destination")
    void shouldCompleteTransferSuccessfully() {
        UUID transferId = UUID.randomUUID();
        ToolTransfer transfer = ToolTransfer.builder()
                .id(transferId)
                .tenantId(tenantId)
                .transferNumber("TRFT-00001")
                .toolId(tool.getId())
                .sourceUnitId(sourceUnitId)
                .destinationUnitId(destUnitId)
                .status(ToolTransferStatus.IN_TRANSIT)
                .build();

        tool.setStatus(ToolStatus.IN_TRANSIT);

        when(transferRepository.findByIdAndTenantId(transferId, tenantId)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(ToolTransfer.class))).thenReturn(transfer);
        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));

        ToolTransferDtos.Response response = transferService.completeTransfer(transferId, tenantId, UUID.randomUUID());

        assertThat(response.status()).isEqualTo(ToolTransferStatus.COMPLETED);
        assertThat(tool.getUnitId()).isEqualTo(destUnitId);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.AVAILABLE);
    }
}
