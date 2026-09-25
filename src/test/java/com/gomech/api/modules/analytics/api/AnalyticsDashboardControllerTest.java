package com.gomech.api.modules.analytics.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.application.AnalyticsDashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsDashboardControllerTest {

    @Mock
    private AnalyticsDashboardService dashboardService;

    @InjectMocks
    private AnalyticsDashboardController controller;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Should return 200 OK with dashboard summary response")
    void shouldReturnDashboardSummary() {
        UUID unitId = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now();

        AnalyticsDtos.DashboardSummaryResponse mockResponse = AnalyticsDtos.DashboardSummaryResponse.builder()
                .tenantId(tenantId)
                .unitId(unitId)
                .startDate(start)
                .endDate(end)
                .heroKpis(List.of())
                .refreshedAt(OffsetDateTime.now())
                .build();

        when(dashboardService.getDashboardSummary(tenantId, unitId, start, end)).thenReturn(mockResponse);

        ResponseEntity<AnalyticsDtos.DashboardSummaryResponse> response = controller.getDashboardSummary(unitId, start, end);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().tenantId()).isEqualTo(tenantId);
        verify(dashboardService).getDashboardSummary(tenantId, unitId, start, end);
    }
}
