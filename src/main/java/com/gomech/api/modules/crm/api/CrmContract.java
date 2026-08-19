package com.gomech.api.modules.crm.api;

import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Public contract exposed by the CRM module for sibling modules (e.g. Operations).
 * Follows ADR-002: cross-module communication happens exclusively via public API contracts or domain events.
 */
public interface CrmContract {

    Optional<CustomerSummaryResponse> findCustomerSummary(UUID customerId, UUID tenantId);

    Optional<VehicleSummaryResponse> findVehicleSummary(UUID vehicleId, UUID tenantId);

    boolean validateCustomerAndVehicleAssociation(UUID customerId, UUID vehicleId, UUID tenantId);
}
