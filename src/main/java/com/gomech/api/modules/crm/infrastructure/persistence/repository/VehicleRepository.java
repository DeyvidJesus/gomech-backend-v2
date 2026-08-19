package com.gomech.api.modules.crm.infrastructure.persistence.repository;

import com.gomech.api.modules.crm.infrastructure.persistence.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<Vehicle> findByTenantIdAndLicensePlateAndDeletedAtIsNull(UUID tenantId, String licensePlate);

    boolean existsByTenantIdAndLicensePlateAndDeletedAtIsNull(UUID tenantId, String licensePlate);

    boolean existsByTenantIdAndLicensePlateAndIdNotAndDeletedAtIsNull(UUID tenantId, String licensePlate, UUID id);

    List<Vehicle> findByCustomerIdAndTenantIdAndDeletedAtIsNull(UUID customerId, UUID tenantId);
}
