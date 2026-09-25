package com.gomech.api.modules.analytics.api;

import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.application.AnalyticsKpiCatalogService;
import com.gomech.api.modules.analytics.domain.KpiCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsKpiControllerTest {

    @Mock
    private AnalyticsKpiCatalogService kpiCatalogService;

    @InjectMocks
    private AnalyticsKpiController controller;

    @Test
    @DisplayName("Should return KPI catalog list")
    void shouldReturnKpiCatalogList() {
        AnalyticsDtos.KpiDefinitionDto dto = AnalyticsDtos.KpiDefinitionDto.builder()
                .code("WO_COMPLETED_COUNT")
                .name("Ordens de Serviço Concluídas")
                .category(KpiCategory.OPERATIONS)
                .unit("unidades")
                .build();

        when(kpiCatalogService.getCatalogDtos()).thenReturn(List.of(dto));

        ResponseEntity<List<AnalyticsDtos.KpiDefinitionDto>> response = controller.listKpiCatalog();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).code()).isEqualTo("WO_COMPLETED_COUNT");
    }
}
