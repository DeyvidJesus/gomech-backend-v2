package com.gomech.api.modules.analytics.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.application.AnalyticsReportService;
import com.gomech.api.modules.analytics.domain.ExportFormat;
import com.gomech.api.modules.analytics.domain.ReportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsReportControllerTest {

    @Mock
    private AnalyticsReportService reportService;

    @InjectMocks
    private AnalyticsReportController controller;

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
    @DisplayName("Should generate report and return 200 OK")
    void shouldGenerateReport() {
        Pageable pageable = PageRequest.of(0, 20);
        AnalyticsDtos.ReportResponse mockResponse = AnalyticsDtos.ReportResponse.builder()
                .reportType(ReportType.OPERATIONAL_SUMMARY)
                .tenantId(tenantId)
                .startDate(LocalDate.now().minusDays(30))
                .endDate(LocalDate.now())
                .summaryMetrics(Map.of("totalCount", 10))
                .columnHeaders(List.of("Data", "OS #", "Status"))
                .rows(List.of())
                .totalElements(0)
                .totalPages(1)
                .currentPage(0)
                .generatedAt(OffsetDateTime.now())
                .build();

        when(reportService.generateReport(eq(tenantId), any(), eq(pageable))).thenReturn(mockResponse);

        ResponseEntity<AnalyticsDtos.ReportResponse> response = controller.generateReport(
                ReportType.OPERATIONAL_SUMMARY, null, null, null, null, null, pageable
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reportType()).isEqualTo(ReportType.OPERATIONAL_SUMMARY);
    }

    @Test
    @DisplayName("Should export report and return file bytes")
    void shouldExportReport() {
        AnalyticsDtos.ExportRequest exportReq = AnalyticsDtos.ExportRequest.builder()
                .reportType(ReportType.OPERATIONAL_SUMMARY)
                .format(ExportFormat.CSV)
                .startDate(LocalDate.now().minusDays(30))
                .endDate(LocalDate.now())
                .build();

        byte[] csvBytes = "Data;OS #;Status\n2026-08-20;OS-1001;COMPLETED\n".getBytes(StandardCharsets.UTF_8);

        when(reportService.exportReport(tenantId, exportReq)).thenReturn(csvBytes);

        ResponseEntity<byte[]> response = controller.exportReport(exportReq);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getContentDisposition().toString()).contains("report-operational_summary");
    }
}
