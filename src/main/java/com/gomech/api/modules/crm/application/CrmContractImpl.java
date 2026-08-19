package com.gomech.api.modules.crm.application;

import com.gomech.api.modules.crm.api.CrmContract;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.crm.domain.CpfCnpjValidator;
import com.gomech.api.modules.crm.domain.LicensePlateValidator;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Customer;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Vehicle;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.CustomerRepository;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CrmContractImpl implements CrmContract {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummaryResponse> findCustomerSummary(UUID customerId, UUID tenantId) {
        return customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)
                .map(this::toCustomerSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleSummaryResponse> findVehicleSummary(UUID vehicleId, UUID tenantId) {
        return vehicleRepository.findByIdAndTenantIdAndDeletedAtIsNull(vehicleId, tenantId)
                .map(this::toVehicleSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateCustomerAndVehicleAssociation(UUID customerId, UUID vehicleId, UUID tenantId) {
        if (customerId == null || vehicleId == null || tenantId == null) {
            return false;
        }

        Optional<Vehicle> vehicleOpt = vehicleRepository.findByIdAndTenantIdAndDeletedAtIsNull(vehicleId, tenantId);
        if (vehicleOpt.isEmpty()) {
            return false;
        }

        Vehicle vehicle = vehicleOpt.get();
        if (vehicle.getCustomer() == null || vehicle.getCustomer().getDeletedAt() != null) {
            return false;
        }

        return vehicle.getCustomer().getId().equals(customerId) && vehicle.getCustomer().getTenantId().equals(tenantId);
    }

    private CustomerSummaryResponse toCustomerSummary(Customer customer) {
        int vehicleCount = customer.getVehicles() != null
                ? (int) customer.getVehicles().stream().filter(v -> v.getDeletedAt() == null).count()
                : 0;

        return new CustomerSummaryResponse(
                customer.getId(),
                customer.getName(),
                customer.getDocument(),
                CpfCnpjValidator.format(customer.getDocument()),
                customer.getPhone(),
                customer.getEmail(),
                vehicleCount,
                customer.getCreatedAt()
        );
    }

    private VehicleSummaryResponse toVehicleSummary(Vehicle vehicle) {
        return new VehicleSummaryResponse(
                vehicle.getId(),
                vehicle.getCustomer().getId(),
                vehicle.getCustomer().getName(),
                vehicle.getLicensePlate(),
                LicensePlateValidator.format(vehicle.getLicensePlate()),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getCurrentMileage()
        );
    }
}
