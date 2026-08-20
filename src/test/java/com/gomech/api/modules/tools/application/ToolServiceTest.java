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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private ToolCategoryRepository categoryRepository;

    @InjectMocks
    private ToolService toolService;

    private UUID tenantId;
    private UUID unitId;
    private UUID categoryId;
    private Tool tool;
    private ToolCategory category;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        category = ToolCategory.builder()
                .id(categoryId)
                .tenantId(tenantId)
                .name("Torquímetros")
                .requiresCalibration(true)
                .defaultMaintenanceIntervalDays(180)
                .build();

        tool = Tool.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .categoryId(categoryId)
                .assetTag("TORQ-01")
                .name("Torquímetro de Estalo 1/2")
                .brand("Gedore")
                .model("Torcofix")
                .status(ToolStatus.AVAILABLE)
                .locationInUnit("Gaveta 02")
                .purchaseCost(BigDecimal.valueOf(850.00))
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Should create tool with calculated calibration due date")
    void shouldCreateToolSuccessfully() {
        CreateToolRequest request = CreateToolRequest.builder()
                .unitId(unitId)
                .categoryId(categoryId)
                .assetTag("TORQ-01")
                .name("Torquímetro de Estalo 1/2")
                .brand("Gedore")
                .model("Torcofix")
                .locationInUnit("Gaveta 02")
                .purchaseCost(BigDecimal.valueOf(850.00))
                .build();

        when(toolRepository.findByTenantIdAndAssetTag(tenantId, "TORQ-01")).thenReturn(Optional.empty());
        when(categoryRepository.findByIdAndTenantId(categoryId, tenantId)).thenReturn(Optional.of(category));
        when(toolRepository.save(any(Tool.class))).thenAnswer(inv -> {
            Tool t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setVersion(0L);
            return t;
        });

        ToolResponse response = toolService.createTool(request, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.assetTag()).isEqualTo("TORQ-01");
        assertThat(response.status()).isEqualTo(ToolStatus.AVAILABLE);
        assertThat(response.nextMaintenanceDueAt()).isNotNull();
        verify(toolRepository).save(any(Tool.class));
    }

    @Test
    @DisplayName("Should reject tool creation if asset tag already exists in tenant")
    void shouldRejectDuplicateAssetTag() {
        CreateToolRequest request = CreateToolRequest.builder()
                .unitId(unitId)
                .assetTag("TORQ-01")
                .name("Torquímetro")
                .build();

        when(toolRepository.findByTenantIdAndAssetTag(tenantId, "TORQ-01")).thenReturn(Optional.of(tool));

        assertThatThrownBy(() -> toolService.createTool(request, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Já existe uma ferramenta com a etiqueta");
    }

    @Test
    @DisplayName("Should update tool details")
    void shouldUpdateToolSuccessfully() {
        UpdateToolRequest request = UpdateToolRequest.builder()
                .assetTag("TORQ-01")
                .name("Torquímetro Atualizado")
                .locationInUnit("Prateleira B")
                .build();

        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));
        when(toolRepository.save(any(Tool.class))).thenReturn(tool);

        ToolResponse response = toolService.updateTool(tool.getId(), request, tenantId);

        assertThat(response.name()).isEqualTo("Torquímetro Atualizado");
        assertThat(response.locationInUnit()).isEqualTo("Prateleira B");
    }

    @Test
    @DisplayName("Should throw ToolNotFoundException when tool does not exist")
    void shouldThrowWhenToolNotFound() {
        UUID fakeId = UUID.randomUUID();
        when(toolRepository.findByIdAndTenantId(fakeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> toolService.getTool(fakeId, tenantId))
                .isInstanceOf(ToolNotFoundException.class);
    }

    @Test
    @DisplayName("Should soft delete tool")
    void shouldSoftDeleteTool() {
        when(toolRepository.findByIdAndTenantId(tool.getId(), tenantId)).thenReturn(Optional.of(tool));

        toolService.deleteTool(tool.getId(), tenantId);

        assertThat(tool.getDeletedAt()).isNotNull();
        verify(toolRepository).save(tool);
    }
}
