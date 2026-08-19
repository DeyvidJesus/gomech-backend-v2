package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;

import java.util.List;
import java.util.UUID;

public record VehicleServiceHistoryResponse(
        UUID vehicleId,
        String licensePlate,
        String formattedLicensePlate,
        String brand,
        String model,
        Integer year,
        Integer currentMileage,
        CustomerSummaryResponse customer,
        VehicleServiceHistoryMetricsResponse metrics,
        List<VehicleHistoricalWorkOrderResponse> workOrders,
        List<VehicleHistoricalInspectionResponse> inspections
) {}
