package com.gomech.api.modules.analytics.api;

import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;

import java.time.LocalDate;
import java.util.UUID;

public interface AnalyticsContract {

    AnalyticsDtos.DashboardSummaryResponse getDashboardSummary(UUID tenantId, UUID unitId, LocalDate startDate, LocalDate endDate);
}
