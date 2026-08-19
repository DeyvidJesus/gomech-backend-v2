package com.gomech.api.modules.operations.infrastructure.persistence.repository;

import com.gomech.api.modules.operations.domain.WorkOrderStatus;
import com.gomech.api.modules.operations.infrastructure.persistence.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID>, JpaSpecificationExecutor<WorkOrder> {

    @Query("SELECT w FROM WorkOrder w LEFT JOIN FETCH w.items WHERE w.id = :id AND w.tenantId = :tenantId AND w.deletedAt IS NULL")
    Optional<WorkOrder> findByIdWithItems(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<WorkOrder> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<WorkOrder> findByTenantIdAndQuoteIdAndDeletedAtIsNull(UUID tenantId, UUID quoteId);

    List<WorkOrder> findByTenantIdAndUnitIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID tenantId,
            UUID unitId,
            Collection<WorkOrderStatus> statuses
    );

    List<WorkOrder> findByTenantIdAndVehicleIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId, UUID vehicleId);

    @Query("SELECT DISTINCT w FROM WorkOrder w LEFT JOIN FETCH w.items WHERE w.tenantId = :tenantId AND w.vehicleId = :vehicleId AND w.status = :status AND w.deletedAt IS NULL ORDER BY w.completedAt DESC NULLS LAST, w.createdAt DESC")
    List<WorkOrder> findCompletedByVehicleWithItems(
            @Param("tenantId") UUID tenantId,
            @Param("vehicleId") UUID vehicleId,
            @Param("status") WorkOrderStatus status
    );
}
