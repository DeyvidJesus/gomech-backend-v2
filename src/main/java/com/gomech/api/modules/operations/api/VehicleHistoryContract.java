package com.gomech.api.modules.operations.api;

import com.gomech.api.modules.operations.api.dto.VehicleServiceHistoryExportResponse;
import com.gomech.api.modules.operations.api.dto.VehicleServiceHistoryResponse;

import java.util.UUID;

/**
 * Public contract exposed by the Operations module for vehicle service history queries and export dossiers.
 * Follows ADR-002: cross-module communication happens exclusively via public API contracts or domain events.
 */
public interface VehicleHistoryContract {

    VehicleServiceHistoryResponse getVehicleServiceHistory(UUID vehicleId, UUID tenantId);

    VehicleServiceHistoryExportResponse getVehicleServiceHistoryExport(UUID vehicleId, UUID tenantId);
}
