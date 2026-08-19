package com.gomech.api.modules.operations.infrastructure.persistence.repository;

import com.gomech.api.modules.operations.infrastructure.persistence.model.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectionRepository extends JpaRepository<Inspection, UUID>, JpaSpecificationExecutor<Inspection> {

    @Query("SELECT i FROM Inspection i LEFT JOIN FETCH i.items WHERE i.id = :id AND i.tenantId = :tenantId AND i.deletedAt IS NULL")
    Optional<Inspection> findByIdWithItems(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<Inspection> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    List<Inspection> findByTenantIdAndVehicleIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId, UUID vehicleId);

    List<Inspection> findByTenantIdAndAppointmentIdAndDeletedAtIsNull(UUID tenantId, UUID appointmentId);
}
