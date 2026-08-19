package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;

public record VehicleServiceHistoryExportResponse(
        String reportId,
        OffsetDateTime generatedAt,
        String workshopName,
        VehicleSummaryResponse vehicle,
        CustomerSummaryResponse customer,
        VehicleServiceHistoryMetricsResponse metrics,
        List<VehicleHistoricalWorkOrderResponse> completedWorkOrders,
        String authenticityVerificationCode,
        String termsAndWarrantyNotice
) {}
