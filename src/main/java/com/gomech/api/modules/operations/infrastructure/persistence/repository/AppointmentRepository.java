package com.gomech.api.modules.operations.infrastructure.persistence.repository;

import com.gomech.api.modules.operations.infrastructure.persistence.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    Optional<Appointment> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<Appointment> findByIdAndTenantIdAndUnitIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID unitId);

    List<Appointment> findByTenantIdAndUnitIdAndScheduledAtBetweenAndDeletedAtIsNullOrderByScheduledAtAsc(
            UUID tenantId,
            UUID unitId,
            OffsetDateTime from,
            OffsetDateTime to
    );

    List<Appointment> findByTenantIdAndScheduledAtBetweenAndDeletedAtIsNullOrderByScheduledAtAsc(
            UUID tenantId,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
