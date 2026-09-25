package com.gomech.api.modules.analytics.application;

import com.gomech.api.modules.analytics.api.AnalyticsContract;
import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsContractImpl implements AnalyticsContract {

    private final AnalyticsDashboardService dashboardService;

    @Override
    public AnalyticsDtos.DashboardSummaryResponse getDashboardSummary(UUID tenantId, UUID unitId, LocalDate startDate, LocalDate endDate) {
        return dashboardService.getDashboardSummary(tenantId, unitId, startDate, endDate);
    }
}
